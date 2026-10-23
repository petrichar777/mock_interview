package com.interviewmaster.backend.service;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.MistakeMapper;
import com.interviewmaster.backend.mapper.PracticeMapper;
import com.interviewmaster.backend.util.ServiceUtils;
import com.interviewmaster.backend.mapper.QuestionMapper;
import com.interviewmaster.backend.mapper.QuestionModuleMapper;
import com.interviewmaster.backend.mapper.QuestionOptionMapper;
import com.interviewmaster.backend.model.dto.response.MistakeDetailResponse;
import com.interviewmaster.backend.model.dto.response.MistakeListResponse;
import com.interviewmaster.backend.model.dto.response.MistakeModuleStatsResponse;
import com.interviewmaster.backend.model.dto.response.MistakeRandomResponse;
import com.interviewmaster.backend.model.dto.response.MistakeRemoveResponse;
import com.interviewmaster.backend.model.dto.response.MistakeStatsResponse;
import com.interviewmaster.backend.model.entity.Question;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.QuestionOption;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MistakeService {

    private static final String MISTAKE_SET_KEY_PREFIX = "user:mistakes:";
    private static final String MODULE_STATS_CACHE_KEY_PREFIX = "user:mistakes:module-stats:";

    private final StringRedisTemplate stringRedisTemplate;
    private final QuestionMapper questionMapper;
    private final QuestionModuleMapper questionModuleMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final PracticeMapper practiceMapper;
    private final MistakeMapper mistakeMapper;
    private final ObjectMapper objectMapper;

    public MistakeListResponse listMistakes(Long userId, Long moduleId, Integer page, Integer size, String sortBy) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        int normalizedPage = ServiceUtils.requirePage(page);
        int normalizedSize = ServiceUtils.requirePageSize(size);
        if (moduleId != null && moduleId <= 0) {
            throw new BusinessException("参数错误：moduleId 非法");
        }
        List<Long> ids = listMistakeQuestionIds(userId);
        if (ids.isEmpty()) {
            return buildEmpty(normalizedPage, normalizedSize);
        }
        int offset = (normalizedPage - 1) * normalizedSize;
        int total = practiceMapper.countMistakeQuestions(userId, ids, moduleId);
        if (total == 0) {
            return buildEmpty(normalizedPage, normalizedSize);
        }
        List<Long> pagedIds = practiceMapper.listMistakeQuestionIdsPaged(userId, ids, moduleId, offset, normalizedSize, sortBy);
        Map<Long, LocalDateTime> lastWrongAtMap = listLastWrongAtMap(userId, pagedIds);
        List<Question> questions = questionMapper.selectBatchIds(pagedIds);
        Map<Long, QuestionModule> moduleMap = buildModuleMap(questions);
        // Preserve SQL sort order (lastWrongAt DESC, question.id DESC)
        Map<Long, Question> questionMap = new HashMap<>();
        if (questions != null) {
            for (Question q : questions) {
                if (q != null && q.getId() != null) {
                    questionMap.put(q.getId(), q);
                }
            }
        }
        List<MistakeListResponse.Item> list = new ArrayList<>();
        for (Long qid : pagedIds) {
            Question question = questionMap.get(qid);
            if (question == null) {
                continue;
            }
            MistakeListResponse.Item item = new MistakeListResponse.Item();
            item.setQuestionId(question.getId());
            item.setModuleId(question.getModuleId());
            QuestionModule module = moduleMap.get(question.getModuleId());
            item.setModuleName(module == null ? null : module.getName());
            item.setType(question.getType());
            item.setDifficulty(question.getDifficulty());
            item.setStem(question.getStem());
            LocalDateTime lastWrongAt = lastWrongAtMap.get(question.getId());
            item.setLastWrongAt(lastWrongAt == null ? null : lastWrongAt.toString());
            list.add(item);
        }
        MistakeListResponse response = new MistakeListResponse();
        response.setList(list);
        response.setPage(normalizedPage);
        response.setSize(normalizedSize);
        response.setTotal(total);
        return response;
    }

    public MistakeRandomResponse randomMistakes(Long userId, Integer count, Long moduleId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        int normalizedCount = normalizeCount(count);
        if (moduleId != null && moduleId <= 0) {
            throw new BusinessException("参数错误：moduleId 非法");
        }
        List<Long> ids = listMistakeQuestionIds(userId);
        if (ids.isEmpty()) {
            MistakeRandomResponse empty = new MistakeRandomResponse();
            empty.setQuestionIds(List.of());
            return empty;
        }
        List<Long> candidates;
        if (moduleId != null) {
            // 在 SQL 层面过滤，避免加载所有错题到内存
            List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .in(Question::getId, ids)
                    .eq(Question::getModuleId, moduleId)
                    .eq(Question::getStatus, 0));
            candidates = new ArrayList<>();
            if (questions != null) {
                for (Question q : questions) {
                    if (q != null && q.getId() != null) {
                        candidates.add(q.getId());
                    }
                }
            }
        } else {
            candidates = ids;
        }
        Collections.shuffle(candidates);
        int end = Math.min(normalizedCount, candidates.size());
        MistakeRandomResponse response = new MistakeRandomResponse();
        response.setQuestionIds(new ArrayList<>(candidates.subList(0, end)));
        return response;
    }

    public MistakeRemoveResponse removeMistake(Long userId, Long questionId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (questionId == null || questionId <= 0) {
            throw new BusinessException("参数错误：questionId 非法");
        }
        String key = MISTAKE_SET_KEY_PREFIX + userId;
        Long removed = stringRedisTemplate.opsForSet().remove(key, String.valueOf(questionId));
        mistakeMapper.deleteByUserAndQuestion(userId, questionId);
        stringRedisTemplate.expire(key, Duration.ofDays(30));
        Long count = stringRedisTemplate.opsForSet().size(key);
        MistakeRemoveResponse response = new MistakeRemoveResponse();
        response.setRemoved(removed != null && removed > 0);
        response.setMistakeCount(ServiceUtils.safeInt(count));
        return response;
    }

    public MistakeStatsResponse getStats(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        LocalDate today = LocalDate.now();
        int todayResolved = countResolvedOnDate(userId, today);
        int consecutiveReviewDays = calcConsecutiveReviewDays(userId, today);
        int weeklyResolved = countWeeklyResolved(userId, today);
        int totalMistakes = ServiceUtils.safeInt(stringRedisTemplate.opsForSet().size(MISTAKE_SET_KEY_PREFIX + userId));

        MistakeStatsResponse response = new MistakeStatsResponse();
        response.setTodayResolved(todayResolved);
        response.setConsecutiveReviewDays(consecutiveReviewDays);
        response.setWeeklyResolved(weeklyResolved);
        response.setTotalMistakes(totalMistakes);
        return response;
    }

    public List<MistakeModuleStatsResponse> getModuleStats(Long userId, Integer limit) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        int normalizedLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 20);

        // 1. Try cache first
        String cacheKey = MODULE_STATS_CACHE_KEY_PREFIX + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return objectMapper.readValue(cached,
                    new TypeReference<List<MistakeModuleStatsResponse>>() {});
            }
        } catch (Exception ignored) {
            // Cache miss or parse error, continue to query DB
        }

        // 2. Query from Redis (mistake question IDs)
        List<Long> mistakeQuestionIds = listMistakeQuestionIds(userId);
        if (mistakeQuestionIds.isEmpty()) {
            return List.of();
        }

        // 3. Query DB for module stats
        List<Map<String, Object>> rawStats = practiceMapper.listMistakeModuleStats(userId, mistakeQuestionIds, normalizedLimit);
        if (rawStats == null || rawStats.isEmpty()) {
            return List.of();
        }

        // 4. Collect module IDs for batch query
        Set<Long> moduleIds = new HashSet<>();
        for (Map<String, Object> row : rawStats) {
            Long moduleId = ServiceUtils.toLongOrNull(row.get("moduleId"));
            if (moduleId != null) {
                moduleIds.add(moduleId);
            }
        }

        // 5. Batch query module names
        Map<Long, String> moduleNameMap = new HashMap<>();
        if (!moduleIds.isEmpty()) {
            List<QuestionModule> modules = questionModuleMapper.selectBatchIds(new ArrayList<>(moduleIds));
            if (modules != null) {
                for (QuestionModule module : modules) {
                    if (module != null && module.getId() != null) {
                        moduleNameMap.put(module.getId(), module.getName());
                    }
                }
            }
        }

        // 6. Build response
        List<MistakeModuleStatsResponse> result = new ArrayList<>();
        for (Map<String, Object> row : rawStats) {
            Long moduleId = ServiceUtils.toLongOrNull(row.get("moduleId"));
            if (moduleId == null) {
                continue;
            }
            MistakeModuleStatsResponse item = new MistakeModuleStatsResponse();
            item.setModuleId(moduleId);
            item.setModuleName(moduleNameMap.getOrDefault(moduleId, "未知模块"));

            int mistakeCount = ServiceUtils.toIntOrDefault(row.get("mistakeCount"), 0);
            int totalAttempts = ServiceUtils.toIntOrDefault(row.get("totalAttempts"), 0);
            int correctAttempts = ServiceUtils.toIntOrDefault(row.get("correctAttempts"), 0);

            item.setMistakeCount(mistakeCount);
            item.setTotalAttempts(totalAttempts);
            item.setCorrectAttempts(correctAttempts);

            double rate = totalAttempts > 0 ? (double) correctAttempts / totalAttempts : 0.0;
            item.setCorrectRate(Math.round(rate * 10000.0) / 100.0); // 百分比，保留2位小数

            result.add(item);
        }

        // 7. Cache result for 5 minutes
        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(cacheKey, json, 5, TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // Cache write failure, non-critical
        }

        return result;
    }

    private int countResolvedOnDate(Long userId, LocalDate date) {
        String key = "user:mistakes:resolved:" + userId + ":" + date;
        Long size = stringRedisTemplate.opsForSet().size(key);
        return ServiceUtils.safeInt(size);
    }

    private int calcConsecutiveReviewDays(Long userId, LocalDate today) {
        int days = 0;
        LocalDate cursor = today;
        int batchSize = 30;
        int totalChecked = 0;
        while (totalChecked < 365) {
            List<String> keys = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize && totalChecked + i < 365; i++) {
                keys.add("user:mistakes:review:" + userId + ":" + cursor.minusDays(i));
            }
            List<String> exists = stringRedisTemplate.opsForValue().multiGet(keys);
            if (exists != null) {
                boolean stopped = false;
                for (String val : exists) {
                    if ("1".equals(val)) {
                        days++;
                    } else {
                        stopped = true;
                        break;
                    }
                }
                if (stopped) {
                    break;
                }
            } else {
                break;
            }
            totalChecked += batchSize;
            cursor = cursor.minusDays(batchSize);
        }
        return days;
    }

    private int countWeeklyResolved(Long userId, LocalDate today) {
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        // 构建本周所有日期的 key 列表
        List<String> keys = new ArrayList<>();
        LocalDate cursor = weekStart;
        while (!cursor.isAfter(today)) {
            keys.add("user:mistakes:resolved:" + userId + ":" + cursor);
            cursor = cursor.plusDays(1);
        }
        // 使用 PIPELINE 一次性获取所有 Set 的 SCARD，避免循环调用
        int total = 0;
        List<Object> results = stringRedisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.setCommands().sCard(key.getBytes());
            }
            return null;
        });
        if (results != null) {
            for (Object result : results) {
                if (result instanceof Long) {
                    total += ((Long) result).intValue();
                }
            }
        }
        return total;
    }

    public MistakeDetailResponse getMistakeDetail(Long userId, Long questionId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (questionId == null || questionId <= 0) {
            throw new BusinessException("参数错误：questionId 非法");
        }
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(MISTAKE_SET_KEY_PREFIX + userId, String.valueOf(questionId));
        if (!Boolean.TRUE.equals(isMember)) {
            throw new BusinessException(40401, "题目不在当前用户错题本中或题目不存在");
        }
        Question question = questionMapper.selectById(questionId);
        if (question == null || question.getStatus() == null || question.getStatus() != 0) {
            throw new BusinessException(40401, "题目不在当前用户错题本中或题目不存在");
        }
        QuestionModule module = questionModuleMapper.selectById(question.getModuleId());
        List<QuestionOption> options = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSort)
                .orderByAsc(QuestionOption::getId));
        List<String> correctAnswers = new ArrayList<>();
        List<MistakeDetailResponse.OptionItem> optionItems = new ArrayList<>();
        if (options != null) {
            for (QuestionOption option : options) {
                if (option == null) {
                    continue;
                }
                MistakeDetailResponse.OptionItem item = new MistakeDetailResponse.OptionItem();
                item.setKey(option.getOptionKey());
                item.setText(option.getOptionText());
                optionItems.add(item);
                if (option.getIsCorrect() != null && option.getIsCorrect() == 1 && option.getOptionKey() != null) {
                    correctAnswers.add(option.getOptionKey().trim().toUpperCase());
                }
            }
        }
        Collections.sort(correctAnswers);
        LocalDateTime lastWrongAt = practiceMapper.getLastWrongAt(userId, questionId);
        String lastWrongAnswersText = practiceMapper.getLastWrongAnswers(userId, questionId);
        MistakeDetailResponse response = new MistakeDetailResponse();
        response.setQuestionId(question.getId());
        response.setModuleId(question.getModuleId());
        response.setModuleName(module == null ? null : module.getName());
        response.setType(question.getType());
        response.setDifficulty(question.getDifficulty());
        response.setStem(question.getStem());
        response.setOptions(optionItems);
        response.setCorrectAnswers(correctAnswers);
        response.setExplanations(ServiceUtils.splitExplanations(question.getAnalysis()));
        response.setTags(ServiceUtils.splitTags(question.getTags()));
        response.setLastWrongAt(lastWrongAt == null ? null : lastWrongAt.toString());
        response.setLastWrongAnswers(ServiceUtils.splitAnswers(lastWrongAnswersText));
        return response;
    }

    private MistakeListResponse buildEmpty(int page, int size) {
        MistakeListResponse response = new MistakeListResponse();
        response.setList(List.of());
        response.setPage(page);
        response.setSize(size);
        response.setTotal(0);
        return response;
    }

    private Map<Long, QuestionModule> buildModuleMap(List<Question> questions) {
        Map<Long, QuestionModule> map = new HashMap<>();
        if (questions == null || questions.isEmpty()) {
            return map;
        }
        Set<Long> moduleIds = new HashSet<>();
        for (Question question : questions) {
            if (question != null && question.getModuleId() != null) {
                moduleIds.add(question.getModuleId());
            }
        }
        if (moduleIds.isEmpty()) {
            return map;
        }
        List<QuestionModule> modules = questionModuleMapper.selectBatchIds(moduleIds);
        if (modules != null) {
            for (QuestionModule module : modules) {
                if (module == null || module.getId() == null) {
                    continue;
                }
                map.put(module.getId(), module);
            }
        }
        return map;
    }

    private int normalizeCount(Integer count) {
        if (count == null) {
            return 10;
        }
        if (count < 1) {
            throw new BusinessException("参数错误：count 必须大于等于 1");
        }
        if (count > 100) {
            throw new BusinessException("参数错误：count 不能超过 100");
        }
        return count;
    }

    private List<Long> listMistakeQuestionIds(Long userId) {
        String key = MISTAKE_SET_KEY_PREFIX + userId;
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        List<Long> ids = new ArrayList<>();
        if (members != null) {
            for (String member : members) {
                if (member == null) {
                    continue;
                }
                try {
                    long id = Long.parseLong(member);
                    if (id > 0) {
                        ids.add(id);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (!ids.isEmpty()) {
            return ids;
        }
        // Redis miss — fall back to MySQL and rebuild Redis cache
        List<Long> dbIds = mistakeMapper.selectQuestionIdsByUserId(userId);
        if (dbIds == null || dbIds.isEmpty()) {
            return List.of();
        }
        for (Long dbId : dbIds) {
            if (dbId != null && dbId > 0) {
                stringRedisTemplate.opsForSet().add(key, String.valueOf(dbId));
                ids.add(dbId);
            }
        }
        if (!ids.isEmpty()) {
            stringRedisTemplate.expire(key, Duration.ofDays(30));
        }
        return ids;
    }

    private Map<Long, LocalDateTime> listLastWrongAtMap(Long userId, List<Long> questionIds) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        if (questionIds == null || questionIds.isEmpty()) {
            return map;
        }
        List<Map<String, Object>> rows = practiceMapper.listLastWrongAtByQuestionIds(userId, questionIds);
        if (rows == null || rows.isEmpty()) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long questionId = ServiceUtils.toLongOrNull(row.get("questionId"));
            LocalDateTime value = ServiceUtils.toLocalDateTime(row.get("lastWrongAt"));
            if (questionId != null && value != null) {
                map.put(questionId, value);
            }
        }
        return map;
    }

}
