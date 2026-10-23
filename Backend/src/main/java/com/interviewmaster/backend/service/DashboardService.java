package com.interviewmaster.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.CategoryMapper;
import com.interviewmaster.backend.mapper.PracticeMapper;
import com.interviewmaster.backend.mapper.QuestionModuleMapper;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.entity.Category;
import com.interviewmaster.backend.util.ServiceUtils;
import com.interviewmaster.backend.model.dto.response.DashboardRecommendedPracticesResponse;
import com.interviewmaster.backend.model.dto.response.DashboardSummaryResponse;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.User;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String SUMMARY_CACHE_KEY_PREFIX = "dashboard:summary:";
    private static final Duration SUMMARY_CACHE_TTL = Duration.ofSeconds(30);

    private final UserMapper userMapper;
    private final QuestionModuleMapper questionModuleMapper;
    private final CategoryMapper categoryMapper;
    private final PracticeMapper practiceMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CheckinService checkinService;
    private final ObjectMapper objectMapper;

    public DashboardSummaryResponse getSummary(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        String cacheKey = SUMMARY_CACHE_KEY_PREFIX + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return objectMapper.readValue(cached, DashboardSummaryResponse.class);
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> practiceStats = userMapper.selectPracticeStats(userId);
        long doneQuestionCount = ServiceUtils.toLongOrZero(practiceStats.get("doneCount"));
        long totalPracticeCount = ServiceUtils.toLongOrZero(practiceStats.get("totalCount"));
        long correctCount = ServiceUtils.toLongOrZero(practiceStats.get("correctCount"));
        long totalPracticeTimeMs = ServiceUtils.toLongOrZero(practiceStats.get("totalTimeMs"));
        long interviewCount = ServiceUtils.safeLong(userMapper.countInterviewSessionByUserId(userId));

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setDoneQuestionCount(ServiceUtils.safeInt(doneQuestionCount));
        response.setCorrectRate(totalPracticeCount == 0 ? 0D : (double) correctCount / totalPracticeCount);
        response.setInterviewCount(ServiceUtils.safeInt(interviewCount));
        response.setTotalPracticeTimeMinutes(ServiceUtils.safeInt(totalPracticeTimeMs / 60000));
        int continuousDays = checkinService.getContinuousDays(userId);
        int baseMaxDaily = continuousDays >= 7 ? 7 : 6;
        User user = userMapper.selectById(userId);
        int extraQuota = (user != null && user.getExtraInterviewQuota() != null) ? user.getExtraInterviewQuota() : 0;
        response.setContinuousCheckinDays(continuousDays);
        response.setRemainingDailyInterviews(calcRemainingDailyInterviews(userId, baseMaxDaily) + extraQuota);
        response.setMaxDailyInterviews(baseMaxDaily);

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), SUMMARY_CACHE_TTL);
        } catch (Exception ignored) {
        }
        return response;
    }

    public DashboardRecommendedPracticesResponse getRecommendedPractices(Long userId, Integer limit) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        int normalizedLimit = normalizeLimit(limit);
        List<QuestionModule> modules = questionModuleMapper.selectList(new LambdaQueryWrapper<QuestionModule>()
                .eq(QuestionModule::getStatus, 0)
                .orderByAsc(QuestionModule::getSort)
                .orderByAsc(QuestionModule::getId));
        Map<Long, String> categoryCodeMap = buildCategoryCodeMap();
        List<DashboardRecommendedPracticesResponse.Item> items = new ArrayList<>();
        if (modules != null && !modules.isEmpty()) {
            Set<Long> allModuleIds = modules.stream().map(QuestionModule::getId).collect(Collectors.toSet());
            Map<Long, Long> availableMap = new HashMap<>();
            List<Map<String, Object>> availableRows = practiceMapper.countAvailableQuestionByModuleIds(new ArrayList<>(allModuleIds));
            if (availableRows != null) {
                for (Map<String, Object> row : availableRows) {
                    Long moduleId = ServiceUtils.toLongOrNull(row.get("moduleId"));
                    Long count = ServiceUtils.toLongOrZero(row.get("total"));
                    if (moduleId != null) {
                        availableMap.put(moduleId, count);
                    }
                }
            }
            Map<Long, Long> doneMap = new HashMap<>();
            List<Map<String, Object>> doneRows = practiceMapper.countDoneQuestionByUserAndModuleIds(userId, new ArrayList<>(allModuleIds));
            if (doneRows != null) {
                for (Map<String, Object> row : doneRows) {
                    Long moduleId = ServiceUtils.toLongOrNull(row.get("moduleId"));
                    Long count = ServiceUtils.toLongOrZero(row.get("total"));
                    if (moduleId != null) {
                        doneMap.put(moduleId, count);
                    }
                }
            }
            for (QuestionModule module : modules) {
                if (items.size() >= normalizedLimit) {
                    break;
                }
                long questionCount = availableMap.getOrDefault(module.getId(), 0L);
                if (questionCount <= 0) {
                    continue;
                }
                long doneCount = doneMap.getOrDefault(module.getId(), 0L);
                DashboardRecommendedPracticesResponse.Item item = new DashboardRecommendedPracticesResponse.Item();
                item.setModuleId(module.getId());
                item.setModuleName(module.getName());
                item.setDescription(module.getDescription());
                item.setCategoryCode(resolveCategoryCode(module, categoryCodeMap));
                if (doneCount > 0) {
                    item.setCtaType("CONTINUE");
                    item.setCtaText("继续练习");
                } else {
                    item.setCtaType("START");
                    item.setCtaText("开始练习");
                }
                items.add(item);
            }
        }
        DashboardRecommendedPracticesResponse response = new DashboardRecommendedPracticesResponse();
        response.setList(items);
        return response;
    }

    private Integer calcContinuousCheckinDays(Long userId) {
        List<LocalDate> checkinDates = userMapper.listCheckinDatesByUserId(userId);
        if (checkinDates == null || checkinDates.isEmpty()) {
            return 0;
        }
        Set<LocalDate> checkinSet = new HashSet<>(checkinDates);
        int days = 0;
        LocalDate cursor = LocalDate.now();
        while (checkinSet.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        return days;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 2;
        }
        if (limit <= 0 || limit > 6) {
            throw new BusinessException("参数错误：limit 取值范围为 1-6");
        }
        return limit;
    }

    private int calcRemainingDailyInterviews(Long userId, int maxDaily) {
        String dailyKey = "interview:daily:" + userId + ":" + LocalDate.now();
        String usedStr = stringRedisTemplate.opsForValue().get(dailyKey);
        int used = 0;
        if (usedStr != null) {
            try {
                used = Integer.parseInt(usedStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(0, maxDaily - used);
    }

    private Map<Long, String> buildCategoryCodeMap() {
        List<Category> categories = categoryMapper.selectList(null);
        Map<Long, String> map = new HashMap<>();
        if (categories != null) {
            for (Category c : categories) {
                map.put(c.getId(), c.getCode());
            }
        }
        return map;
    }

    private String resolveCategoryCode(QuestionModule module, Map<Long, String> categoryCodeMap) {
        if (module.getCategoryId() != null && categoryCodeMap.containsKey(module.getCategoryId())) {
            return categoryCodeMap.get(module.getCategoryId());
        }
        String text = ((module.getCategoryName() == null ? "" : module.getCategoryName()) + " "
                + (module.getName() == null ? "" : module.getName())).toLowerCase(Locale.ROOT);
        if (text.contains("前端") || text.contains("javascript") || text.contains("vue") || text.contains("react")) {
            return "frontend";
        }
        if (text.contains("算法") || text.contains("leetcode")) {
            return "algorithm";
        }
        return "backend";
    }
}
