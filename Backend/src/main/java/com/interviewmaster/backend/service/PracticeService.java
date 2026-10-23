package com.interviewmaster.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.CategoryMapper;
import com.interviewmaster.backend.mapper.DirectionMapper;
import com.interviewmaster.backend.mapper.MistakeMapper;
import com.interviewmaster.backend.mapper.PracticeMapper;
import com.interviewmaster.backend.mapper.QuestionMapper;
import com.interviewmaster.backend.mapper.QuestionModuleMapper;
import com.interviewmaster.backend.model.dto.request.PracticeSubmitAnswerRequest;
import com.interviewmaster.backend.model.dto.response.PracticeAddMistakeResponse;
import com.interviewmaster.backend.model.dto.response.PracticeModuleListResponse;
import com.interviewmaster.backend.model.dto.response.PracticeNextQuestionResponse;
import com.interviewmaster.backend.model.dto.response.PracticeSubmitAnswerResponse;
import com.interviewmaster.backend.model.entity.Category;
import com.interviewmaster.backend.model.entity.Direction;
import com.interviewmaster.backend.model.entity.Question;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.QuestionOption;
import com.interviewmaster.backend.mapper.QuestionOptionMapper;
import com.interviewmaster.backend.util.ServiceUtils;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final String MISTAKE_SET_KEY_PREFIX = "user:mistakes:";

    private final QuestionModuleMapper questionModuleMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final PracticeMapper practiceMapper;
    private final MistakeMapper mistakeMapper;
    private final CategoryMapper categoryMapper;
    private final DirectionMapper directionMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public PracticeModuleListResponse listModules(Long userId, String category) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        List<Direction> allDirections = directionMapper.selectList(new LambdaQueryWrapper<Direction>()
                .eq(Direction::getStatus, 0)
                .orderByAsc(Direction::getSort)
                .orderByAsc(Direction::getId));
        List<Category> allCategories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 0)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, String> categoryCodeMap = new java.util.HashMap<>();
        Map<Long, Long> categoryDirectionMap = new java.util.HashMap<>();
        for (Category c : allCategories) {
            categoryCodeMap.put(c.getId(), c.getCode());
            categoryDirectionMap.put(c.getId(), c.getDirectionId());
        }
        String activeDirectionCode = null;
        for (Category c : allCategories) {
            if (category != null && category.equals(c.getCode())) {
                activeDirectionCode = c.getDirectionId() != null
                        ? allDirections.stream().filter(d -> d.getId().equals(c.getDirectionId())).map(Direction::getCode).findFirst().orElse(null)
                        : null;
                break;
            }
        }
        String normalizedCategory = normalizeCategory(category, allCategories);
        List<QuestionModule> modules = questionModuleMapper.selectList(new LambdaQueryWrapper<QuestionModule>()
                .eq(QuestionModule::getStatus, 0)
                .orderByAsc(QuestionModule::getSort)
                .orderByAsc(QuestionModule::getId));
        PracticeModuleListResponse response = new PracticeModuleListResponse();
        response.setDirections(buildDirections(allDirections, activeDirectionCode));
        response.setCategories(buildCategories(allCategories, normalizedCategory));
        response.setList(buildModuleItems(modules, userId, normalizedCategory, categoryCodeMap));
        response.setMistakeEntry(buildMistakeEntry(userId));
        return response;
    }

    public PracticeNextQuestionResponse getNextQuestion(Long userId, Long moduleId, String mode) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (moduleId == null || moduleId <= 0) {
            throw new BusinessException("参数错误：moduleId 非法");
        }
        String normalizedMode = normalizeMode(mode);
        List<Question> candidates = listCandidatesByMode(userId, moduleId, normalizedMode);
        if (candidates.isEmpty()) {
            throw new BusinessException("当前暂无可练习题目");
        }
        Set<Long> doneIdSet = listDoneIdSetByMode(userId, moduleId, candidates, normalizedMode);
        int currentIndex = findNextQuestionIndex(candidates, doneIdSet);
        return buildPracticeQuestionResponse(candidates, currentIndex, normalizedMode);
    }

    public PracticeNextQuestionResponse getPrevQuestion(Long userId, Long moduleId, String mode) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (moduleId == null || moduleId <= 0) {
            throw new BusinessException("参数错误：moduleId 非法");
        }
        String normalizedMode = normalizeMode(mode);
        List<Question> candidates = listCandidatesByMode(userId, moduleId, normalizedMode);
        if (candidates.isEmpty()) {
            throw new BusinessException("当前暂无可练习题目");
        }
        Set<Long> doneIdSet = listDoneIdSetByMode(userId, moduleId, candidates, normalizedMode);
        int nextIndex = findNextQuestionIndex(candidates, doneIdSet);
        int prevIndex = findPrevQuestionIndex(candidates, doneIdSet, nextIndex);
        return buildPracticeQuestionResponse(candidates, prevIndex, normalizedMode);
    }

    @Transactional
    public PracticeSubmitAnswerResponse submitAnswer(Long userId, Long questionId, PracticeSubmitAnswerRequest request) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (questionId == null || questionId <= 0) {
            throw new BusinessException("参数错误：questionId 非法");
        }
        if (request == null || request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new BusinessException("参数错误：answers 不能为空");
        }
        // 防重复提交：同一用户同一题目 10 秒内不允许重复提交
        String dedupKey = "practice:submit:" + userId + ":" + questionId;
        Boolean dedupOk = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofSeconds(10));
        if (dedupOk == null || !dedupOk) {
            throw new BusinessException("请勿重复提交，稍后再试");
        }
        String mode = normalizeMode(request.getMode());
        Question question = questionMapper.selectById(questionId);
        if (question == null || question.getStatus() == null || question.getStatus() != 0) {
            throw new BusinessException("题目不存在");
        }
        QuestionModule module = questionModuleMapper.selectById(question.getModuleId());
        if (module == null || module.getStatus() == null || module.getStatus() != 0) {
            throw new BusinessException("模块不存在");
        }
        List<QuestionOption> optionEntities = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSort)
                .orderByAsc(QuestionOption::getId));
        if (optionEntities == null || optionEntities.isEmpty()) {
            throw new BusinessException("题目选项缺失");
        }
        Set<String> validKeys = new HashSet<>();
        List<String> correctAnswers = new ArrayList<>();
        for (QuestionOption option : optionEntities) {
            if (option.getOptionKey() == null) {
                continue;
            }
            String key = option.getOptionKey().trim().toUpperCase(Locale.ROOT);
            validKeys.add(key);
            if (option.getIsCorrect() != null && option.getIsCorrect() == 1) {
                correctAnswers.add(key);
            }
        }
        if (correctAnswers.isEmpty()) {
            throw new BusinessException("题目答案配置缺失");
        }
        Set<String> userAnswers = normalizeAnswerKeys(request.getAnswers());
        if (userAnswers.isEmpty()) {
            throw new BusinessException("参数错误：answers 不能为空");
        }
        for (String answer : userAnswers) {
            if (!validKeys.contains(answer)) {
                throw new BusinessException("参数错误：answers 包含非法选项");
            }
        }
        boolean correct = isAnswerCorrect(userAnswers, correctAnswers);
        String answersText = joinAnswers(userAnswers);
        long timeCostMs = normalizeTimeCostMs(request.getTimeCostMs());
        int inserted = practiceMapper.insertPracticeRecord(
                userId,
                question.getModuleId(),
                questionId,
                answersText,
                correct ? 1 : 0,
                timeCostMs,
                LocalDateTime.now()
        );
        if (inserted != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
        boolean autoAddedToMistakes = false;
        boolean autoRemovedFromMistakes = false;
        String mistakeKey = MISTAKE_SET_KEY_PREFIX + userId;
        if (correct) {
            // 移除错题：Redis remove 返回被移除的数量，>0 表示之前在集合中
            Long removed = stringRedisTemplate.opsForSet().remove(mistakeKey, String.valueOf(questionId));
            if (removed != null && removed > 0) {
                autoRemovedFromMistakes = true;
                // 只有确实从 Redis 移除了，才同步删除 MySQL 记录并标记为已解决
                try {
                    mistakeMapper.deleteByUserAndQuestion(userId, questionId);
                } catch (Exception ex) {
                    log.error("Failed to delete mistake from MySQL after Redis removal. userId={}, questionId={}",
                            userId, questionId, ex);
                }
                String resolvedKey = "user:mistakes:resolved:" + userId + ":" + LocalDate.now();
                stringRedisTemplate.opsForSet().add(resolvedKey, String.valueOf(questionId));
                stringRedisTemplate.expire(resolvedKey, Duration.ofDays(30));
            }
        } else {
            // 添加错题：SADD 返回 1 表示新加入（之前不在集合中），0 表示已存在
            Long added = stringRedisTemplate.opsForSet().add(mistakeKey, String.valueOf(questionId));
            stringRedisTemplate.expire(mistakeKey, Duration.ofDays(30));
            // 只有新加入错题时才写入 MySQL（使用 upsert 处理重复情况）
            if (added != null && added > 0) {
                try {
                    mistakeMapper.upsert(userId, questionId);
                } catch (Exception ex) {
                    log.error("Failed to upsert mistake to MySQL after Redis add. userId={}, questionId={}",
                            userId, questionId, ex);
                }
                autoAddedToMistakes = true;
            }
        }
        if ("mistake".equals(mode)) {
            String reviewKey = "user:mistakes:review:" + userId + ":" + LocalDate.now();
            stringRedisTemplate.opsForValue().set(reviewKey, "1", Duration.ofDays(30));
        }
        List<Question> candidates = listCandidatesByMode(userId, question.getModuleId(), mode);
        Set<Long> doneIdSet = listDoneIdSetByMode(userId, question.getModuleId(), candidates, mode);
        doneIdSet.add(questionId);
        boolean hasNext = hasNextQuestion(candidates, doneIdSet);
        PracticeSubmitAnswerResponse response = new PracticeSubmitAnswerResponse();
        response.setCorrect(correct);
        Collections.sort(correctAnswers);
        response.setCorrectAnswers(correctAnswers);
        response.setExplanations(ServiceUtils.splitExplanations(question.getAnalysis()));
        response.setTags(ServiceUtils.splitTags(question.getTags()));
        response.setAutoAddedToMistakes(autoAddedToMistakes);
        response.setAutoRemovedFromMistakes(autoRemovedFromMistakes);
        response.setHasNext(hasNext);
        response.setFinished(!hasNext);
        return response;
    }

    public PracticeAddMistakeResponse addMistake(Long userId, Long questionId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (questionId == null || questionId <= 0) {
            throw new BusinessException("参数错误：questionId 非法");
        }
        Question question = questionMapper.selectById(questionId);
        if (question == null || question.getStatus() == null || question.getStatus() != 0) {
            throw new BusinessException("题目不存在");
        }
        String key = MISTAKE_SET_KEY_PREFIX + userId;
        stringRedisTemplate.opsForSet().add(key, String.valueOf(questionId));
        stringRedisTemplate.expire(key, Duration.ofDays(30));
        mistakeMapper.upsert(userId, questionId);
        Long count = stringRedisTemplate.opsForSet().size(key);
        PracticeAddMistakeResponse response = new PracticeAddMistakeResponse();
        response.setAdded(true);
        response.setMistakeCount(ServiceUtils.safeInt(count));
        return response;
    }

    private String normalizeCategory(String category, List<Category> allCategories) {
        if (category == null || category.isBlank()) {
            return "all";
        }
        String value = category.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(value)) {
            return "all";
        }
        for (Category c : allCategories) {
            if (value.equals(c.getCode())) {
                return value;
            }
        }
        throw new BusinessException("参数错误：category 不支持该分类");
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "normal";
        }
        String value = mode.trim().toLowerCase(Locale.ROOT);
        if (!"normal".equals(value) && !"mistake".equals(value)) {
            throw new BusinessException("参数错误：mode 仅支持 normal/mistake");
        }
        return value;
    }

    private Set<Long> listDoneIdSet(Long userId, Long moduleId) {
        List<Long> doneIds = practiceMapper.listDoneQuestionIdsByUserAndModule(userId, moduleId);
        return doneIds == null ? new HashSet<>() : new HashSet<>(doneIds);
    }

    private List<PracticeModuleListResponse.DirectionItem> buildDirections(List<Direction> allDirections, String activeDirectionCode) {
        List<PracticeModuleListResponse.DirectionItem> directions = new ArrayList<>();
        for (Direction d : allDirections) {
            PracticeModuleListResponse.DirectionItem item = new PracticeModuleListResponse.DirectionItem();
            item.setId(d.getId());
            item.setCode(d.getCode());
            item.setName(d.getName());
            item.setActive(d.getCode().equals(activeDirectionCode));
            directions.add(item);
        }
        return directions;
    }

    private List<PracticeModuleListResponse.CategoryItem> buildCategories(List<Category> allCategories, String activeCategory) {
        List<PracticeModuleListResponse.CategoryItem> categories = new ArrayList<>();
        categories.add(buildCategory(null, "all", "全部分类", activeCategory));
        for (Category c : allCategories) {
            categories.add(buildCategory(c.getDirectionId(), c.getCode(), c.getName(), activeCategory));
        }
        return categories;
    }

    private PracticeModuleListResponse.CategoryItem buildCategory(Long directionId, String code, String name, String activeCategory) {
        PracticeModuleListResponse.CategoryItem item = new PracticeModuleListResponse.CategoryItem();
        item.setDirectionId(directionId);
        item.setCode(code);
        item.setName(name);
        item.setActive(code.equals(activeCategory));
        return item;
    }

    private List<PracticeModuleListResponse.ModuleItem> buildModuleItems(List<QuestionModule> modules, Long userId, String category, Map<Long, String> categoryCodeMap) {
        List<PracticeModuleListResponse.ModuleItem> list = new ArrayList<>();
        if (modules == null || modules.isEmpty()) {
            return list;
        }
        List<Long> moduleIds = new ArrayList<>();
        for (QuestionModule module : modules) {
            if (module != null && module.getId() != null) {
                moduleIds.add(module.getId());
            }
        }
        Map<Long, Integer> totalMap = toCountMap(practiceMapper.countAvailableQuestionByModuleIds(moduleIds));
        Map<Long, Integer> doneMap = toCountMap(practiceMapper.countDoneQuestionByUserAndModuleIds(userId, moduleIds));
        Map<Long, List<Integer>> typeMap = toTypeMap(practiceMapper.listQuestionTypeRowsByModuleIds(moduleIds));
        for (QuestionModule module : modules) {
            String categoryCode = resolveCategoryCode(module, categoryCodeMap);
            if (!"all".equals(category) && !category.equals(categoryCode)) {
                continue;
            }
            int total = totalMap.getOrDefault(module.getId(), 0);
            int done = doneMap.getOrDefault(module.getId(), 0);
            int percent = calcProgressPercent(done, total);
            PracticeModuleListResponse.ModuleItem item = new PracticeModuleListResponse.ModuleItem();
            item.setId(module.getId());
            item.setCategoryCode(categoryCode);
            item.setCategoryName(module.getCategoryName());
            item.setName(module.getName());
            item.setDescription(module.getDescription());
            item.setQuestionTypes(toQuestionTypes(typeMap.get(module.getId())));
            item.setTotalQuestionCount(total);
            item.setDoneQuestionCount(done);
            item.setProgressPercent(percent);
            item.setCtaType(buildCtaType(done, total));
            item.setCtaText(buildCtaText(done, total));
            list.add(item);
        }
        return list;
    }

    private Map<Long, Integer> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Integer> map = new java.util.HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long moduleId = ServiceUtils.toLongOrNull(row.get("moduleId"));
            Integer total = ServiceUtils.toIntegerOrNull(row.get("total"));
            if (moduleId != null && total != null) {
                map.put(moduleId, total);
            }
        }
        return map;
    }

    private Map<Long, List<Integer>> toTypeMap(List<Map<String, Object>> rows) {
        Map<Long, List<Integer>> map = new java.util.HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long moduleId = ServiceUtils.toLongOrNull(row.get("moduleId"));
            Integer type = ServiceUtils.toIntegerOrNull(row.get("type"));
            if (moduleId == null || type == null) {
                continue;
            }
            map.computeIfAbsent(moduleId, k -> new ArrayList<>()).add(type);
        }
        return map;
    }

    private List<Question> listCandidatesByMode(Long userId, Long moduleId, String mode) {
        if (!"mistake".equals(mode)) {
            QuestionModule module = questionModuleMapper.selectById(moduleId);
            if (module == null || module.getStatus() == null || module.getStatus() != 0) {
                throw new BusinessException("模块不存在");
            }
            List<Question> normalQuestions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .eq(Question::getModuleId, moduleId)
                    .eq(Question::getStatus, 0)
                    .orderByAsc(Question::getId));
            if (normalQuestions == null || normalQuestions.isEmpty()) {
                throw new BusinessException("当前模块暂无题目");
            }
            return normalQuestions;
        }
        Set<Long> mistakeIds = listMistakeIdSet(userId);
        if (mistakeIds.isEmpty()) {
            return List.of();
        }
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, mistakeIds)
                .eq(Question::getStatus, 0)
                .orderByAsc(Question::getId));
    }

    private Set<Long> listMistakeIdSet(Long userId) {
        Set<String> members = stringRedisTemplate.opsForSet().members(MISTAKE_SET_KEY_PREFIX + userId);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        Set<Long> mistakeIds = new HashSet<>();
        for (String member : members) {
            if (member == null) {
                continue;
            }
            try {
                long id = Long.parseLong(member);
                if (id > 0) {
                    mistakeIds.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return mistakeIds;
    }

    private Set<Long> listDoneIdSetByMode(Long userId, Long moduleId, List<Question> candidates, String mode) {
        if (!"mistake".equals(mode)) {
            return listDoneIdSet(userId, moduleId);
        }
        if (candidates == null || candidates.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> questionIds = new ArrayList<>();
        for (Question candidate : candidates) {
            if (candidate.getId() != null) {
                questionIds.add(candidate.getId());
            }
        }
        if (questionIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> doneIds = practiceMapper.listDoneQuestionIdsByUserAndQuestionIds(userId, questionIds);
        return doneIds == null ? new HashSet<>() : new HashSet<>(doneIds);
    }

    private int findNextQuestionIndex(List<Question> questions, Set<Long> doneIdSet) {
        if (doneIdSet == null || doneIdSet.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < questions.size(); i++) {
            Long questionId = questions.get(i).getId();
            if (questionId == null || !doneIdSet.contains(questionId)) {
                return i;
            }
        }
        return 0;
    }

    private int findPrevQuestionIndex(List<Question> questions, Set<Long> doneIdSet, int nextIndex) {
        if (questions == null || questions.isEmpty()) {
            return 0;
        }
        if (nextIndex > 0) {
            return nextIndex - 1;
        }
        if (doneIdSet == null || doneIdSet.isEmpty()) {
            return 0;
        }
        boolean allDone = true;
        for (Question question : questions) {
            if (question.getId() == null || !doneIdSet.contains(question.getId())) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            return questions.size() - 1;
        }
        return 0;
    }

    private PracticeNextQuestionResponse buildPracticeQuestionResponse(List<Question> candidates, int index, String mode) {
        Question current = candidates.get(index);
        QuestionModule module = current.getModuleId() == null ? null : questionModuleMapper.selectById(current.getModuleId());
        List<QuestionOption> optionEntities = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, current.getId())
                .orderByAsc(QuestionOption::getSort)
                .orderByAsc(QuestionOption::getId));
        PracticeNextQuestionResponse response = new PracticeNextQuestionResponse();
        response.setQuestionId(current.getId());
        response.setModuleId(current.getModuleId());
        response.setModuleName(module == null ? null : module.getName());
        response.setMode(mode);
        response.setType(current.getType());
        response.setTypeText(toTypeText(current.getType()));
        response.setDifficulty(current.getDifficulty());
        response.setDifficultyText(toDifficultyText(current.getDifficulty()));
        response.setStem(current.getStem());
        response.setOptions(toOptionItems(optionEntities));
        int progressCurrent = index + 1;
        int progressTotal = candidates.size();
        response.setProgressCurrent(progressCurrent);
        response.setProgressTotal(progressTotal);
        response.setProgressPercent(progressTotal <= 0 ? 0 : (int) Math.floor(progressCurrent * 100D / progressTotal));
        response.setHasPrev(progressCurrent > 1);
        response.setHasNext(progressCurrent < progressTotal);
        return response;
    }

    private List<PracticeNextQuestionResponse.OptionItem> toOptionItems(List<QuestionOption> options) {
        List<PracticeNextQuestionResponse.OptionItem> list = new ArrayList<>();
        if (options == null || options.isEmpty()) {
            return list;
        }
        for (QuestionOption option : options) {
            PracticeNextQuestionResponse.OptionItem item = new PracticeNextQuestionResponse.OptionItem();
            item.setKey(option.getOptionKey());
            item.setText(option.getOptionText());
            list.add(item);
        }
        return list;
    }

    private Set<String> normalizeAnswerKeys(List<String> answers) {
        Set<String> set = new HashSet<>();
        if (answers == null || answers.isEmpty()) {
            return set;
        }
        for (String answer : answers) {
            if (answer == null) {
                continue;
            }
            String value = answer.trim().toUpperCase(Locale.ROOT);
            if (!value.isEmpty()) {
                set.add(value);
            }
        }
        return set;
    }

    private boolean isAnswerCorrect(Set<String> userAnswers, List<String> correctAnswers) {
        if (userAnswers == null || correctAnswers == null) {
            return false;
        }
        if (userAnswers.size() != correctAnswers.size()) {
            return false;
        }
        for (String answer : correctAnswers) {
            if (!userAnswers.contains(answer)) {
                return false;
            }
        }
        return true;
    }

    private String joinAnswers(Set<String> answers) {
        if (answers == null || answers.isEmpty()) {
            return null;
        }
        List<String> list = new ArrayList<>(answers);
        Collections.sort(list);
        return String.join(",", list);
    }

    private long normalizeTimeCostMs(Long timeCostMs) {
        if (timeCostMs == null || timeCostMs <= 0) {
            return 0L;
        }
        long max = 2L * 60 * 60 * 1000;
        if (timeCostMs > max) {
            return max;
        }
        return timeCostMs;
    }

    private boolean hasNextQuestion(List<Question> candidates, Set<Long> doneIdSet) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (Question question : candidates) {
            if (question.getId() == null || !doneIdSet.contains(question.getId())) {
                return true;
            }
        }
        return false;
    }

    private String toTypeText(Integer type) {
        if (type == null) {
            return null;
        }
        if (type == 1) {
            return "单选题";
        }
        if (type == 2) {
            return "多选题";
        }
        if (type == 3) {
            return "判断题";
        }
        return null;
    }

    private String toDifficultyText(Integer difficulty) {
        if (difficulty == null) {
            return null;
        }
        if (difficulty == 1) {
            return "简单";
        }
        if (difficulty == 2) {
            return "中等";
        }
        if (difficulty == 3) {
            return "困难";
        }
        return null;
    }

    private PracticeModuleListResponse.MistakeEntry buildMistakeEntry(Long userId) {
        long count = ServiceUtils.safeLong(stringRedisTemplate.opsForSet().size(MISTAKE_SET_KEY_PREFIX + userId));
        PracticeModuleListResponse.MistakeEntry entry = new PracticeModuleListResponse.MistakeEntry();
        entry.setMistakeCount(ServiceUtils.safeInt(count));
        entry.setEnabled(count > 0);
        entry.setButtonText(count > 0 ? "进入错题重练" : "暂无错题");
        return entry;
    }

    private String resolveCategoryCode(QuestionModule module, Map<Long, String> categoryCodeMap) {
        if (module.getCategoryId() != null && categoryCodeMap.containsKey(module.getCategoryId())) {
            return categoryCodeMap.get(module.getCategoryId());
        }
        // 降级兜底：旧数据没有 category_id，用关键词匹配
        return toCategoryCode(module.getCategoryName(), module.getName());
    }

    private String toCategoryCode(String categoryName, String moduleName) {
        String text = ((categoryName == null ? "" : categoryName) + " " + (moduleName == null ? "" : moduleName))
                .toLowerCase(Locale.ROOT);
        if (text.contains("前端") || text.contains("javascript") || text.contains("vue") || text.contains("react")) {
            return "frontend";
        }
        if (text.contains("算法") || text.contains("leetcode")) {
            return "algorithm";
        }
        return "backend";
    }

    private List<String> toQuestionTypes(List<Integer> types) {
        List<String> result = new ArrayList<>();
        if (types == null || types.isEmpty()) {
            return result;
        }
        for (Integer type : types) {
            if (type == null) {
                continue;
            }
            if (type == 1) {
                result.add("单选");
            } else if (type == 2) {
                result.add("多选");
            } else if (type == 3) {
                result.add("判断");
            }
        }
        return result;
    }

    private int calcProgressPercent(int done, int total) {
        if (total <= 0 || done <= 0) {
            return 0;
        }
        if (done >= total) {
            return 100;
        }
        return (int) Math.floor(done * 100D / total);
    }

    private String buildCtaType(int done, int total) {
        if (done <= 0) {
            return "START";
        }
        if (total > 0 && done >= total) {
            return "RESTART";
        }
        return "CONTINUE";
    }

    private String buildCtaText(int done, int total) {
        if (done <= 0) {
            return "开始练习";
        }
        if (total > 0 && done >= total) {
            return "重新练习";
        }
        return "继续练习";
    }

}
