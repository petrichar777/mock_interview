package com.interviewmaster.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.CategoryMapper;
import com.interviewmaster.backend.mapper.DirectionMapper;
import com.interviewmaster.backend.mapper.QuestionModuleMapper;
import com.interviewmaster.backend.mapper.QuestionMapper;
import com.interviewmaster.backend.mapper.QuestionOptionMapper;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.dto.request.AdminModuleUpsertRequest;
import com.interviewmaster.backend.model.dto.request.AdminQuestionUpsertRequest;
import com.interviewmaster.backend.model.dto.request.CategoryStatusRequest;
import com.interviewmaster.backend.model.dto.request.CategoryUpsertRequest;
import com.interviewmaster.backend.model.dto.request.DirectionStatusRequest;
import com.interviewmaster.backend.model.dto.request.DirectionUpsertRequest;
import com.interviewmaster.backend.model.dto.request.GenerateQuestionsRequest;
import com.interviewmaster.backend.model.dto.request.GrantQuotaRequest;
import com.interviewmaster.backend.model.dto.request.RegisterRequest;
import com.interviewmaster.backend.model.dto.response.AdminQuestionImportResponse;
import com.interviewmaster.backend.model.dto.response.AdminQuestionDetailResponse;
import com.interviewmaster.backend.model.dto.response.AdminQuestionListItemResponse;
import com.interviewmaster.backend.model.dto.response.AdminModuleListItemResponse;
import com.interviewmaster.backend.model.dto.response.AdminUserListItemResponse;
import com.interviewmaster.backend.model.dto.response.GenerateQuestionsResponse;
import com.interviewmaster.backend.model.dto.response.ReviewDraftQuestionResponse;
import com.interviewmaster.backend.model.dto.response.ReviewDraftQuestionsResponse;
import com.interviewmaster.backend.model.entity.Category;
import com.interviewmaster.backend.model.entity.Direction;
import com.interviewmaster.backend.model.entity.Question;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.QuestionOption;
import com.interviewmaster.backend.model.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final DirectionMapper directionMapper;
    private final QuestionModuleMapper questionModuleMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final PasswordEncoder passwordEncoder;
    private final DeepSeekService deepSeekService;
    private final ZhipuService zhipuService;
    private final TransactionTemplate transactionTemplate;

    private final ExecutorService reviewExecutor = Executors.newFixedThreadPool(5);
    private final ConcurrentHashMap<String, BatchReviewState> reviewStates = new ConcurrentHashMap<>();

    @Transactional
    public Long registerAdmin(RegisterRequest request) {
        String username = request.getUsername() == null ? null : request.getUsername().trim();
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        if (username == null || username.isBlank()) {
            throw new BusinessException("参数错误：username 不能为空");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException("参数错误：email 不能为空");
        }
        if (existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }
        if (existsByEmail(email)) {
            throw new BusinessException("邮箱已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("ADMIN");
        user.setStatus(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            int inserted = userMapper.insert(user);
            if (inserted != 1 || user.getId() == null) {
                throw new BusinessException(500, "服务器内部错误");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("用户名或邮箱已存在");
        }
        return user.getId();
    }

    public Map<String, Object> listUsers(Long requesterId, String keyword, String role, Integer status, Integer page, Integer size) {
        assertAdmin(requesterId);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String normalizedRole = normalizeRole(role);
        Integer normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = keyword == null ? null : keyword.trim();

        LambdaQueryWrapper<User> countWrapper = buildListQueryWrapper(normalizedKeyword, normalizedRole, normalizedStatus);
        Long total = userMapper.selectCount(countWrapper);

        Page<User> userPage = new Page<>(normalizedPage, normalizedSize);
        LambdaQueryWrapper<User> listWrapper = buildListQueryWrapper(normalizedKeyword, normalizedRole, normalizedStatus);
        listWrapper.orderByDesc(User::getCreatedAt);
        Page<User> pageResult = userMapper.selectPage(userPage, listWrapper);
        List<AdminUserListItemResponse> list = pageResult.getRecords().stream().map(this::toAdminUserListItemResponse).toList();

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("page", normalizedPage);
        data.put("size", normalizedSize);
        data.put("total", total == null ? 0L : total);
        return data;
    }

    @Transactional
    public void updateUserStatus(Long requesterId, Long userId, Integer status) {
        assertAdmin(requesterId);
        if (userId == null || userId <= 0) {
            throw new BusinessException("参数错误：userId 非法");
        }
        Integer normalizedStatus = normalizeStatus(status);
        if (normalizedStatus == null) {
            throw new BusinessException("参数错误：status 不能为空");
        }
        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        if ("ADMIN".equals(targetUser.getRole()) && normalizedStatus == 1) {
            throw new BusinessException("管理员账号不允许被禁用");
        }
        targetUser.setStatus(normalizedStatus);
        targetUser.setUpdatedAt(LocalDateTime.now());
        int updated = userMapper.updateById(targetUser);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
    }

    @Transactional
    public void grantQuota(Long requesterId, Long userId, GrantQuotaRequest request) {
        assertAdmin(requesterId);
        if (userId == null || userId <= 0) {
            throw new BusinessException("参数错误：userId 非法");
        }
        Integer interviewQuota = request.getInterviewQuota() != null ? request.getInterviewQuota() : 0;
        Integer resumeQuota = request.getResumeQuota() != null ? request.getResumeQuota() : 0;
        if (interviewQuota < 0 || resumeQuota < 0) {
            throw new BusinessException("配额不能为负数");
        }
        if (interviewQuota == 0 && resumeQuota == 0) {
            return;
        }
        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        if (interviewQuota > 0) {
            targetUser.setExtraInterviewQuota(
                    (targetUser.getExtraInterviewQuota() != null ? targetUser.getExtraInterviewQuota() : 0) + interviewQuota);
        }
        if (resumeQuota > 0) {
            targetUser.setExtraResumeQuota(
                    (targetUser.getExtraResumeQuota() != null ? targetUser.getExtraResumeQuota() : 0) + resumeQuota);
        }
        targetUser.setUpdatedAt(LocalDateTime.now());
        int updated = userMapper.updateById(targetUser);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
    }

    @Transactional
    public void clearQuota(Long requesterId, Long userId) {
        assertAdmin(requesterId);
        if (userId == null || userId <= 0) {
            throw new BusinessException("参数错误：userId 非法");
        }
        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在");
        }
        userMapper.clearExtraQuota(userId);
    }

    public Map<String, Object> listModules(Long requesterId, Long categoryId, String categoryName, Integer status) {
        assertAdmin(requesterId);
        Integer normalizedStatus = normalizeStatus(status);

        Map<Long, Category> categoryMap = buildCategoryMap();
        Map<Long, Direction> directionMap = buildDirectionMap();

        LambdaQueryWrapper<QuestionModule> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null && categoryId > 0) {
            wrapper.eq(QuestionModule::getCategoryId, categoryId);
        } else if (categoryName != null && !categoryName.isBlank()) {
            wrapper.like(QuestionModule::getCategoryName, categoryName.trim());
        }
        if (normalizedStatus != null) {
            wrapper.eq(QuestionModule::getStatus, normalizedStatus);
        }
        wrapper.orderByAsc(QuestionModule::getSort).orderByAsc(QuestionModule::getId);
        List<QuestionModule> modules = questionModuleMapper.selectList(wrapper);

        Map<Long, Integer> questionCountMap = Map.of();
        if (!modules.isEmpty()) {
            List<Long> moduleIds = modules.stream().map(QuestionModule::getId).toList();
            questionCountMap = questionMapper.countByModuleIds(moduleIds).stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("moduleId")).longValue(),
                            m -> ((Number) m.get("cnt")).intValue()));
        }
        final Map<Long, Integer> finalCountMap = questionCountMap;
        List<AdminModuleListItemResponse> list = modules.stream()
                .map(m -> {
                    Category c = categoryMap.get(m.getCategoryId());
                    Direction d = c != null ? directionMap.get(c.getDirectionId()) : null;
                    AdminModuleListItemResponse resp = toAdminModuleListItemResponse(m, c, d);
                    resp.setQuestionCount(finalCountMap.getOrDefault(m.getId(), 0));
                    return resp;
                })
                .toList();
        return Map.of("list", list);
    }

    @Transactional
    public Long createModule(Long requesterId, AdminModuleUpsertRequest request) {
        assertAdmin(requesterId);
        QuestionModule module = new QuestionModule();
        fillModuleByRequest(module, request, false);
        LocalDateTime now = LocalDateTime.now();
        module.setCreatedAt(now);
        module.setUpdatedAt(now);
        int inserted = questionModuleMapper.insert(module);
        if (inserted != 1 || module.getId() == null) {
            throw new BusinessException(500, "服务器内部错误");
        }
        return module.getId();
    }

    @Transactional
    public void updateModule(Long requesterId, Long moduleId, AdminModuleUpsertRequest request) {
        assertAdmin(requesterId);
        if (moduleId == null || moduleId <= 0) {
            throw new BusinessException("参数错误：moduleId 非法");
        }
        QuestionModule module = questionModuleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException("模块不存在");
        }
        fillModuleByRequest(module, request, true);
        module.setUpdatedAt(LocalDateTime.now());
        int updated = questionModuleMapper.updateById(module);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
    }

    @Transactional
    public void deleteModule(Long requesterId, Long moduleId) {
        assertAdmin(requesterId);
        if (moduleId == null || moduleId <= 0) {
            throw new BusinessException("参数错误：moduleId 非法");
        }
        QuestionModule module = questionModuleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException("模块不存在");
        }
        List<Long> questionIds = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getModuleId, moduleId)
                        .select(Question::getId))
                .stream()
                .map(Question::getId)
                .toList();
        if (!questionIds.isEmpty()) {
            questionOptionMapper.delete(
                    new LambdaQueryWrapper<QuestionOption>()
                            .in(QuestionOption::getQuestionId, questionIds));
            questionMapper.delete(
                    new LambdaQueryWrapper<Question>()
                            .eq(Question::getModuleId, moduleId));
        }
        questionModuleMapper.deleteById(moduleId);
    }

    // ====== Direction CRUD ======

    public List<Direction> listDirections(Long requesterId) {
        assertAdmin(requesterId);
        return directionMapper.selectList(new LambdaQueryWrapper<Direction>()
                .orderByAsc(Direction::getSort)
                .orderByAsc(Direction::getId));
    }

    @Transactional
    public Long createDirection(Long requesterId, DirectionUpsertRequest request) {
        assertAdmin(requesterId);
        String code = request.getCode() == null ? null : request.getCode().trim().toLowerCase(Locale.ROOT);
        String name = request.getName() == null ? null : request.getName().trim();
        if (code == null || code.isBlank()) {
            throw new BusinessException("参数错误：code 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("参数错误：name 不能为空");
        }
        if (directionMapper.selectCount(new LambdaQueryWrapper<Direction>().eq(Direction::getCode, code)) > 0) {
            throw new BusinessException("方向代码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Direction direction = new Direction();
        direction.setCode(code);
        direction.setName(name);
        direction.setSort(request.getSort() != null ? request.getSort() : 0);
        direction.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        direction.setCreatedAt(now);
        direction.setUpdatedAt(now);
        int inserted = directionMapper.insert(direction);
        if (inserted != 1 || direction.getId() == null) {
            throw new BusinessException(500, "服务器内部错误");
        }
        return direction.getId();
    }

    @Transactional
    public void updateDirection(Long requesterId, Long directionId, DirectionUpsertRequest request) {
        assertAdmin(requesterId);
        if (directionId == null || directionId <= 0) {
            throw new BusinessException("参数错误：directionId 非法");
        }
        Direction direction = directionMapper.selectById(directionId);
        if (direction == null) {
            throw new BusinessException("方向不存在");
        }
        String code = request.getCode() == null ? null : request.getCode().trim().toLowerCase(Locale.ROOT);
        String name = request.getName() == null ? null : request.getName().trim();
        if (code == null || code.isBlank()) {
            throw new BusinessException("参数错误：code 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("参数错误：name 不能为空");
        }
        Long dup = directionMapper.selectCount(new LambdaQueryWrapper<Direction>()
                .eq(Direction::getCode, code)
                .ne(Direction::getId, directionId));
        if (dup != null && dup > 0) {
            throw new BusinessException("方向代码已存在");
        }
        direction.setCode(code);
        direction.setName(name);
        if (request.getSort() != null) {
            direction.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            direction.setStatus(request.getStatus());
        }
        direction.setUpdatedAt(LocalDateTime.now());
        int updated = directionMapper.updateById(direction);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
    }

    @Transactional
    public void updateDirectionStatus(Long requesterId, Long directionId, DirectionStatusRequest request) {
        assertAdmin(requesterId);
        if (directionId == null || directionId <= 0) {
            throw new BusinessException("参数错误：directionId 非法");
        }
        Direction direction = directionMapper.selectById(directionId);
        if (direction == null) {
            throw new BusinessException("方向不存在");
        }
        direction.setStatus(request.getStatus());
        direction.setUpdatedAt(LocalDateTime.now());
        directionMapper.updateById(direction);
    }

    @Transactional
    public void deleteDirection(Long requesterId, Long directionId) {
        assertAdmin(requesterId);
        if (directionId == null || directionId <= 0) {
            throw new BusinessException("参数错误：directionId 非法");
        }
        Direction direction = directionMapper.selectById(directionId);
        if (direction == null) {
            throw new BusinessException("方向不存在");
        }
        List<Long> categoryIds = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getDirectionId, directionId)
                        .select(Category::getId))
                .stream().map(Category::getId).toList();
        if (!categoryIds.isEmpty()) {
            List<Long> moduleIds = questionModuleMapper.selectList(
                    new LambdaQueryWrapper<QuestionModule>()
                            .in(QuestionModule::getCategoryId, categoryIds)
                            .select(QuestionModule::getId))
                    .stream().map(QuestionModule::getId).toList();
            if (!moduleIds.isEmpty()) {
                List<Long> questionIds = questionMapper.selectList(
                        new LambdaQueryWrapper<Question>()
                                .in(Question::getModuleId, moduleIds)
                                .select(Question::getId))
                        .stream().map(Question::getId).toList();
                if (!questionIds.isEmpty()) {
                    questionOptionMapper.delete(
                            new LambdaQueryWrapper<QuestionOption>()
                                    .in(QuestionOption::getQuestionId, questionIds));
                    questionMapper.delete(
                            new LambdaQueryWrapper<Question>()
                                    .in(Question::getModuleId, moduleIds));
                }
                questionModuleMapper.delete(
                        new LambdaQueryWrapper<QuestionModule>()
                                .in(QuestionModule::getCategoryId, categoryIds));
            }
            categoryMapper.delete(
                    new LambdaQueryWrapper<Category>()
                            .eq(Category::getDirectionId, directionId));
        }
        directionMapper.deleteById(directionId);
    }

    // ====== Category CRUD ======

    public List<Category> listCategories(Long requesterId) {
        assertAdmin(requesterId);
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    @Transactional
    public Long createCategory(Long requesterId, CategoryUpsertRequest request) {
        assertAdmin(requesterId);
        String code = request.getCode() == null ? null : request.getCode().trim().toLowerCase(Locale.ROOT);
        String name = request.getName() == null ? null : request.getName().trim();
        Long directionId = request.getDirectionId();
        if (directionId == null || directionId <= 0) {
            throw new BusinessException("参数错误：directionId 非法");
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException("参数错误：code 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("参数错误：name 不能为空");
        }
        if (directionMapper.selectById(directionId) == null) {
            throw new BusinessException("所属方向不存在");
        }
        if (categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getCode, code)) > 0) {
            throw new BusinessException("分类代码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Category category = new Category();
        category.setDirectionId(directionId);
        category.setCode(code);
        category.setName(name);
        category.setSort(request.getSort() != null ? request.getSort() : 0);
        category.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        int inserted = categoryMapper.insert(category);
        if (inserted != 1 || category.getId() == null) {
            throw new BusinessException(500, "服务器内部错误");
        }
        return category.getId();
    }

    @Transactional
    public void updateCategory(Long requesterId, Long categoryId, CategoryUpsertRequest request) {
        assertAdmin(requesterId);
        if (categoryId == null || categoryId <= 0) {
            throw new BusinessException("参数错误：categoryId 非法");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        Long directionId = request.getDirectionId();
        if (directionId == null || directionId <= 0) {
            throw new BusinessException("参数错误：directionId 非法");
        }
        if (directionMapper.selectById(directionId) == null) {
            throw new BusinessException("所属方向不存在");
        }
        String code = request.getCode() == null ? null : request.getCode().trim().toLowerCase(Locale.ROOT);
        String name = request.getName() == null ? null : request.getName().trim();
        if (code == null || code.isBlank()) {
            throw new BusinessException("参数错误：code 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("参数错误：name 不能为空");
        }
        Long dup = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getCode, code)
                .ne(Category::getId, categoryId));
        if (dup != null && dup > 0) {
            throw new BusinessException("分类代码已存在");
        }
        category.setDirectionId(directionId);
        category.setCode(code);
        category.setName(name);
        if (request.getSort() != null) {
            category.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
        category.setUpdatedAt(LocalDateTime.now());
        int updated = categoryMapper.updateById(category);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
    }

    @Transactional
    public void updateCategoryStatus(Long requesterId, Long categoryId, CategoryStatusRequest request) {
        assertAdmin(requesterId);
        if (categoryId == null || categoryId <= 0) {
            throw new BusinessException("参数错误：categoryId 非法");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        category.setStatus(request.getStatus());
        category.setUpdatedAt(LocalDateTime.now());
        categoryMapper.updateById(category);
    }

    @Transactional
    public void deleteCategory(Long requesterId, Long categoryId) {
        assertAdmin(requesterId);
        if (categoryId == null || categoryId <= 0) {
            throw new BusinessException("参数错误：categoryId 非法");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        List<Long> moduleIds = questionModuleMapper.selectList(
                new LambdaQueryWrapper<QuestionModule>()
                        .eq(QuestionModule::getCategoryId, categoryId)
                        .select(QuestionModule::getId))
                .stream().map(QuestionModule::getId).toList();
        if (!moduleIds.isEmpty()) {
            List<Long> questionIds = questionMapper.selectList(
                    new LambdaQueryWrapper<Question>()
                            .in(Question::getModuleId, moduleIds)
                            .select(Question::getId))
                    .stream().map(Question::getId).toList();
            if (!questionIds.isEmpty()) {
                questionOptionMapper.delete(
                        new LambdaQueryWrapper<QuestionOption>()
                                .in(QuestionOption::getQuestionId, questionIds));
                questionMapper.delete(
                        new LambdaQueryWrapper<Question>()
                                .in(Question::getModuleId, moduleIds));
            }
            questionModuleMapper.delete(
                    new LambdaQueryWrapper<QuestionModule>()
                            .eq(QuestionModule::getCategoryId, categoryId));
        }
        categoryMapper.deleteById(categoryId);
    }

    public Map<String, Object> listQuestions(Long requesterId, String keyword, Long moduleId, Integer type, Integer difficulty,
                                             Integer status, Integer page, Integer size) {
        assertAdmin(requesterId);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        Long normalizedModuleId = normalizeModuleId(moduleId);
        Integer normalizedType = normalizeQuestionType(type);
        Integer normalizedDifficulty = normalizeQuestionDifficulty(difficulty);
        Integer normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = keyword == null ? null : keyword.trim();

        LambdaQueryWrapper<Question> countWrapper = buildQuestionListQueryWrapper(
                normalizedKeyword, normalizedModuleId, normalizedType, normalizedDifficulty, normalizedStatus);
        Long total = questionMapper.selectCount(countWrapper);

        Page<Question> questionPage = new Page<>(normalizedPage, normalizedSize);
        LambdaQueryWrapper<Question> listWrapper = buildQuestionListQueryWrapper(
                normalizedKeyword, normalizedModuleId, normalizedType, normalizedDifficulty, normalizedStatus);
        listWrapper.orderByDesc(Question::getUpdatedAt).orderByDesc(Question::getId);
        Page<Question> pageResult = questionMapper.selectPage(questionPage, listWrapper);
        List<Question> questions = pageResult.getRecords();
        Map<Long, String> moduleNameMap = loadModuleNameMap(questions);
        List<AdminQuestionListItemResponse> list = questions.stream()
                .map(question -> toAdminQuestionListItemResponse(question, moduleNameMap.get(question.getModuleId())))
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("page", normalizedPage);
        data.put("size", normalizedSize);
        data.put("total", total == null ? 0L : total);
        return data;
    }

    public AdminQuestionDetailResponse getQuestionDetail(Long requesterId, Long questionId) {
        assertAdmin(requesterId);
        if (questionId == null || questionId <= 0) {
            throw new BusinessException("参数错误：questionId 非法");
        }
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        List<QuestionOption> options = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSort).orderByAsc(QuestionOption::getId));
        AdminQuestionDetailResponse response = new AdminQuestionDetailResponse();
        response.setId(question.getId());
        response.setModuleId(question.getModuleId());
        response.setType(question.getType());
        response.setDifficulty(question.getDifficulty());
        response.setStem(question.getStem());
        response.setAnalysis(question.getAnalysis());
        response.setTags(question.getTags());
        response.setStatus(question.getStatus());
        response.setOptions(options.stream().map(this::toAdminQuestionOptionItem).toList());
        return response;
    }

    @Transactional
    public Long createQuestion(Long requesterId, AdminQuestionUpsertRequest request) {
        assertAdmin(requesterId);
        validateQuestionUpsertRequest(request);
        assertModuleExists(request.getModuleId());

        LocalDateTime now = LocalDateTime.now();
        Question question = new Question();
        fillQuestionByRequest(question, request);
        question.setCreatedBy(requesterId);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        int inserted = questionMapper.insert(question);
        if (inserted != 1 || question.getId() == null) {
            throw new BusinessException(500, "服务器内部错误");
        }
        saveQuestionOptions(question.getId(), request.getOptions());
        return question.getId();
    }

    @Transactional
    public void updateQuestion(Long requesterId, Long questionId, AdminQuestionUpsertRequest request) {
        assertAdmin(requesterId);
        if (questionId == null || questionId <= 0) {
            throw new BusinessException("参数错误：questionId 非法");
        }
        validateQuestionUpsertRequest(request);
        assertModuleExists(request.getModuleId());

        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        fillQuestionByRequest(question, request);
        LocalDateTime now = LocalDateTime.now();
        question.setUpdatedAt(now);
        int updated = questionMapper.updateById(question);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>().eq(QuestionOption::getQuestionId, questionId));
        saveQuestionOptions(questionId, request.getOptions());
    }

    @Transactional
    public void deleteQuestion(Long requesterId, Long questionId) {
        assertAdmin(requesterId);
        if (questionId == null || questionId <= 0) {
            throw new BusinessException("参数错误：questionId 非法");
        }
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>().eq(QuestionOption::getQuestionId, questionId));
        int deleted = questionMapper.deleteById(questionId);
        if (deleted != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
    }

    @Transactional
    public void updateQuestionStatus(Long requesterId, Long questionId, Integer status) {
        assertAdmin(requesterId);
        if (questionId == null || questionId <= 0) {
            throw new BusinessException("参数错误：questionId 非法");
        }
        Integer normalizedStatus = normalizeStatus(status);
        if (normalizedStatus == null) {
            throw new BusinessException("参数错误：status 不能为空");
        }
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        question.setStatus(normalizedStatus);
        question.setUpdatedAt(LocalDateTime.now());
        int updated = questionMapper.updateById(question);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
    }

    @Transactional
    public AdminQuestionImportResponse importQuestions(Long requesterId, MultipartFile file) {
        assertAdmin(requesterId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("参数错误：file 不能为空");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过50MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new BusinessException("参数错误：仅支持 .md 文件");
        }
        String markdown;
        try {
            markdown = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BusinessException("文件读取失败");
        }
        List<QuestionImportDraft> drafts = parseQuestionImportMarkdown(markdown);
        if (drafts.isEmpty()) {
            throw new BusinessException("导入文件中未解析到题目");
        }
        List<AdminQuestionImportResponse.FailedItem> failedItems = new ArrayList<>();
        int successCount = 0;
        for (int i = 0; i < drafts.size(); i++) {
            QuestionImportDraft draft = drafts.get(i);
            try {
                upsertQuestionByDraft(draft, requesterId);
                successCount++;
            } catch (BusinessException ex) {
                failedItems.add(buildImportFailedItem(i + 1, draft.stem, ex.getMessage()));
            } catch (Exception ex) {
                failedItems.add(buildImportFailedItem(i + 1, draft.stem, "导入失败"));
            }
        }
        AdminQuestionImportResponse response = new AdminQuestionImportResponse();
        response.setTotal(drafts.size());
        response.setSuccessCount(successCount);
        response.setFailedCount(failedItems.size());
        response.setFailedItems(failedItems);
        return response;
    }

    private LambdaQueryWrapper<User> buildListQueryWrapper(String keyword, String role, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getEmail, keyword));
        }
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<Question> buildQuestionListQueryWrapper(String keyword, Long moduleId, Integer type,
                                                                       Integer difficulty, Integer status) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Question::getStem, keyword);
        }
        if (moduleId != null) {
            wrapper.eq(Question::getModuleId, moduleId);
        }
        if (type != null) {
            wrapper.eq(Question::getType, type);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        if (status != null) {
            wrapper.eq(Question::getStatus, status);
        }
        return wrapper;
    }

    private AdminUserListItemResponse toAdminUserListItemResponse(User user) {
        AdminUserListItemResponse response = new AdminUserListItemResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt() == null ? null : user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.setExtraInterviewQuota(user.getExtraInterviewQuota());
        response.setExtraResumeQuota(user.getExtraResumeQuota());
        return response;
    }

    private AdminModuleListItemResponse toAdminModuleListItemResponse(QuestionModule module, Category category, Direction direction) {
        AdminModuleListItemResponse response = new AdminModuleListItemResponse();
        response.setId(module.getId());
        response.setCategoryId(module.getCategoryId());
        response.setCategoryName(module.getCategoryName());
        response.setCategoryCode(category != null ? category.getCode() : null);
        response.setDirectionId(direction != null ? direction.getId() : null);
        response.setDirectionName(direction != null ? direction.getName() : null);
        response.setDirectionCode(direction != null ? direction.getCode() : null);
        response.setName(module.getName());
        response.setStatus(module.getStatus());
        response.setSort(module.getSort());
        return response;
    }

    private AdminQuestionListItemResponse toAdminQuestionListItemResponse(Question question, String moduleName) {
        AdminQuestionListItemResponse response = new AdminQuestionListItemResponse();
        response.setId(question.getId());
        response.setStem(truncate(question.getStem(), 100));
        response.setType(question.getType());
        response.setDifficulty(question.getDifficulty());
        response.setModuleId(question.getModuleId());
        response.setModuleName(moduleName);
        response.setStatus(question.getStatus());
        response.setUpdatedAt(question.getUpdatedAt() == null ? null : question.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return response;
    }

    private AdminQuestionDetailResponse.OptionItem toAdminQuestionOptionItem(QuestionOption option) {
        AdminQuestionDetailResponse.OptionItem item = new AdminQuestionDetailResponse.OptionItem();
        item.setKey(option.getOptionKey());
        item.setText(option.getOptionText());
        item.setCorrect(option.getIsCorrect() != null && option.getIsCorrect() == 1);
        item.setSort(option.getSort());
        return item;
    }

    private void fillQuestionByRequest(Question question, AdminQuestionUpsertRequest request) {
        question.setModuleId(request.getModuleId());
        question.setType(normalizeQuestionType(request.getType()));
        question.setDifficulty(normalizeQuestionDifficulty(request.getDifficulty()));
        question.setStem(request.getStem() == null ? null : request.getStem().trim());
        question.setAnalysis(request.getAnalysis() == null ? null : request.getAnalysis().trim());
        question.setTags(request.getTags() == null ? null : request.getTags().trim());
        Integer normalizedStatus = normalizeStatus(request.getStatus());
        question.setStatus(normalizedStatus == null ? 0 : normalizedStatus);
    }

    private void saveQuestionOptions(Long questionId, List<AdminQuestionUpsertRequest.OptionItem> options) {
        for (AdminQuestionUpsertRequest.OptionItem option : options) {
            QuestionOption entity = new QuestionOption();
            entity.setQuestionId(questionId);
            entity.setOptionKey(option.getKey().trim().toUpperCase(Locale.ROOT));
            entity.setOptionText(option.getText() == null ? null : option.getText().trim());
            entity.setIsCorrect(Boolean.TRUE.equals(option.getCorrect()) ? 1 : 0);
            entity.setSort(option.getSort() == null ? 0 : option.getSort());
            int inserted = questionOptionMapper.insert(entity);
            if (inserted != 1) {
                throw new BusinessException(500, "服务器内部错误");
            }
        }
    }

    private void validateQuestionUpsertRequest(AdminQuestionUpsertRequest request) {
        if (request.getModuleId() == null || request.getModuleId() <= 0) {
            throw new BusinessException("参数错误：moduleId 非法");
        }
        normalizeQuestionType(request.getType());
        normalizeQuestionDifficulty(request.getDifficulty());
        if (request.getStem() == null || request.getStem().isBlank()) {
            throw new BusinessException("参数错误：stem 不能为空");
        }
        List<AdminQuestionUpsertRequest.OptionItem> options = request.getOptions();
        if (options == null || options.isEmpty()) {
            throw new BusinessException("参数错误：options 不能为空");
        }
        if (options.size() > 10) {
            throw new BusinessException("参数错误：options 不能超过10项");
        }
        Set<String> keySet = new HashSet<>();
        int correctCount = 0;
        for (AdminQuestionUpsertRequest.OptionItem option : options) {
            if (option == null) {
                throw new BusinessException("参数错误：options 存在空项");
            }
            String key = option.getKey() == null ? null : option.getKey().trim().toUpperCase(Locale.ROOT);
            if (key == null || key.isBlank()) {
                throw new BusinessException("参数错误：option key 不能为空");
            }
            if (!keySet.add(key)) {
                throw new BusinessException("参数错误：option key 不能重复");
            }
            if (option.getSort() != null && option.getSort() < 0) {
                throw new BusinessException("参数错误：option sort 不能小于0");
            }
            if (Boolean.TRUE.equals(option.getCorrect())) {
                correctCount++;
            }
            if (request.getType() != null && request.getType() != 3) {
                String text = option.getText() == null ? null : option.getText().trim();
                if (text == null || text.isBlank()) {
                    throw new BusinessException("参数错误：非判断题选项 text 不能为空");
                }
            }
        }
        Integer type = request.getType();
        if (type != null && type == 1 && correctCount != 1) {
            throw new BusinessException("参数错误：单选题必须且仅能有一个正确选项");
        }
        if (type != null && type == 2 && correctCount < 1) {
            throw new BusinessException("参数错误：多选题至少有一个正确选项");
        }
        if (type != null && type == 3) {
            List<String> keys = new ArrayList<>(keySet);
            if (keys.size() != 2 || !keySet.contains("T") || !keySet.contains("F")) {
                throw new BusinessException("参数错误：判断题选项必须为 T/F");
            }
            if (correctCount != 1) {
                throw new BusinessException("参数错误：判断题必须且仅能有一个正确选项");
            }
        }
    }

    private void assertModuleExists(Long moduleId) {
        QuestionModule module = questionModuleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException("模块不存在");
        }
    }

    private Map<Long, String> loadModuleNameMap(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return Map.of();
        }
        Set<Long> moduleIds = new HashSet<>();
        for (Question question : questions) {
            if (question.getModuleId() != null) {
                moduleIds.add(question.getModuleId());
            }
        }
        if (moduleIds.isEmpty()) {
            return Map.of();
        }
        List<QuestionModule> modules = questionModuleMapper.selectBatchIds(moduleIds);
        Map<Long, String> map = new HashMap<>();
        for (QuestionModule module : modules) {
            map.put(module.getId(), module.getName());
        }
        return map;
    }

    private Map<Long, Category> buildCategoryMap() {
        List<Category> categories = categoryMapper.selectList(null);
        Map<Long, Category> map = new HashMap<>();
        if (categories != null) {
            for (Category c : categories) {
                map.put(c.getId(), c);
            }
        }
        return map;
    }

    private Map<Long, Direction> buildDirectionMap() {
        List<Direction> directions = directionMapper.selectList(null);
        Map<Long, Direction> map = new HashMap<>();
        if (directions != null) {
            for (Direction d : directions) {
                map.put(d.getId(), d);
            }
        }
        return map;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void fillModuleByRequest(QuestionModule module, AdminModuleUpsertRequest request, boolean forUpdate) {
        Long categoryId = request.getCategoryId();
        String name = request.getName() == null ? null : request.getName().trim();
        if (categoryId == null || categoryId <= 0) {
            throw new BusinessException("参数错误：categoryId 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("参数错误：name 不能为空");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || (category.getStatus() != null && category.getStatus() != 0)) {
            throw new BusinessException("分类不存在或已禁用");
        }
        module.setCategoryId(categoryId);
        module.setCategoryName(category.getName());
        module.setName(name);
        module.setDescription(request.getDescription() == null ? null : request.getDescription().trim());

        Integer sort = request.getSort();
        if (sort != null && sort < 0) {
            throw new BusinessException("参数错误：sort 不能小于0");
        }
        if (sort != null) {
            module.setSort(sort);
        } else if (!forUpdate || module.getSort() == null) {
            module.setSort(0);
        }

        Integer normalizedStatus = normalizeStatus(request.getStatus());
        if (normalizedStatus != null) {
            module.setStatus(normalizedStatus);
        } else if (!forUpdate || module.getStatus() == null) {
            module.setStatus(0);
        }
    }

    private void assertAdmin(Long requesterId) {
        if (requesterId == null) {
            throw new BusinessException(401, "未登录");
        }
        User user = userMapper.selectById(requesterId);
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        if (!"ADMIN".equals(user.getRole())) {
            throw new BusinessException(403, "无权限");
        }
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            throw new BusinessException("参数错误：page 非法");
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1 || size > 100) {
            throw new BusinessException("参数错误：size 取值范围为 1-100");
        }
        return size;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String upper = role.trim().toUpperCase();
        if (!"USER".equals(upper) && !"ADMIN".equals(upper)) {
            throw new BusinessException("参数错误：role 仅支持 USER/ADMIN");
        }
        return upper;
    }

    private Integer normalizeStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (status != 0 && status != 1 && status != 2) {
            throw new BusinessException("参数错误：status 仅支持 0/1/2");
        }
        return status;
    }

    private Long normalizeModuleId(Long moduleId) {
        if (moduleId == null) {
            return null;
        }
        if (moduleId <= 0) {
            throw new BusinessException("参数错误：moduleId 非法");
        }
        return moduleId;
    }

    private Integer normalizeQuestionType(Integer type) {
        if (type == null) {
            return null;
        }
        if (type != 1 && type != 2 && type != 3) {
            throw new BusinessException("参数错误：type 仅支持 1/2/3");
        }
        return type;
    }

    private Integer normalizeQuestionDifficulty(Integer difficulty) {
        if (difficulty == null) {
            return null;
        }
        if (difficulty != 1 && difficulty != 2 && difficulty != 3) {
            throw new BusinessException("参数错误：difficulty 仅支持 1/2/3");
        }
        return difficulty;
    }

    private boolean existsByUsername(String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        return count != null && count > 0;
    }

    private boolean existsByEmail(String email) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        return count != null && count > 0;
    }

    private List<QuestionImportDraft> parseQuestionImportMarkdown(String markdown) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("(?m)^---+\\s*$");
        List<QuestionImportDraft> result = new ArrayList<>();
        for (String block : blocks) {
            String text = block == null ? "" : block.trim();
            if (text.isBlank()) {
                continue;
            }
            if (!looksLikeQuestionBlock(text)) {
                continue;
            }
            QuestionImportDraft draft = parseQuestionBlock(text);
            if (draft.options.isEmpty() && draft.answer != null) {
                fillOptionsFromAnswerOnly(draft);
            }
            result.add(draft);
        }
        return result;
    }

    private boolean looksLikeQuestionBlock(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("moduleid") || lower.contains("stem")
                || lower.contains("type") || lower.contains("options");
    }

    private QuestionImportDraft parseQuestionBlock(String text) {
        QuestionImportDraft draft = new QuestionImportDraft();
        String[] lines = text.split("\n");
        boolean inOptions = false;
        int optionSort = 1;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            String normalizedLine = line.replace("**", "");
            int separatorIndex = findKeyValueSeparator(normalizedLine);
            if (separatorIndex > 0) {
                String rawKey = normalizedLine.substring(0, separatorIndex)
                        .replaceFirst("^[\\-\\*\\s]+", "").trim();
                String key = normalizeImportKey(rawKey);
                String value = normalizedLine.substring(separatorIndex + 1).trim();
                if (isOptionFieldKey(key)) {
                    inOptions = true;
                    continue;
                }
                if (isTopFieldKey(key)) {
                    inOptions = false;
                    applyQuestionField(draft, key, value);
                    continue;
                }
            }
            if (!inOptions) {
                continue;
            }
            OptionParseResult option = parseOptionLine(normalizedLine, optionSort);
            if (option == null) {
                continue;
            }
            draft.options.add(option.item);
            if (option.correctByMarker) {
                draft.correctKeys.add(option.item.getKey());
            }
            optionSort++;
        }
        applyCorrectAnswerKeys(draft);
        return draft;
    }

    private int findKeyValueSeparator(String line) {
        int cn = line.indexOf('：');
        int en = line.indexOf(':');
        if (cn < 0) {
            return en;
        }
        if (en < 0) {
            return cn;
        }
        return Math.min(cn, en);
    }

    private String normalizeImportKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "");
        return switch (normalized) {
            case "moduleid", "module" -> "moduleId";
            case "type", "题型" -> "type";
            case "difficulty", "难度" -> "difficulty";
            case "stem", "题干" -> "stem";
            case "analysis", "解析" -> "analysis";
            case "tags", "标签" -> "tags";
            case "status", "状态" -> "status";
            case "answer", "答案", "correct" -> "answer";
            case "options", "选项" -> "options";
            default -> normalized;
        };
    }

    private boolean isTopFieldKey(String key) {
        return "moduleId".equals(key) || "type".equals(key) || "difficulty".equals(key) || "stem".equals(key)
                || "analysis".equals(key) || "tags".equals(key) || "status".equals(key) || "answer".equals(key);
    }

    private boolean isOptionFieldKey(String key) {
        return "options".equals(key);
    }

    private void applyQuestionField(QuestionImportDraft draft, String key, String value) {
        switch (key) {
            case "moduleId" -> draft.moduleId = parseLongValue(value);
            case "type" -> draft.type = parseTypeValue(value);
            case "difficulty" -> draft.difficulty = parseDifficultyValue(value);
            case "stem" -> draft.stem = trimToNull(value);
            case "analysis" -> draft.analysis = trimToNull(value);
            case "tags" -> draft.tags = trimToNull(value);
            case "status" -> draft.status = parseIntegerValue(value);
            case "answer" -> draft.answer = trimToNull(value);
            default -> {
            }
        }
    }

    private OptionParseResult parseOptionLine(String line, int sort) {
        String clean = line.replaceFirst("^[\\-\\*\\s]+", "").trim();
        if (clean.isBlank()) {
            return null;
        }
        String key;
        String text;
        int dot = clean.indexOf('.');
        int separator = clean.indexOf('：');
        if (separator < 0) {
            separator = clean.indexOf(':');
        }
        if (dot > 0 && dot <= 3) {
            key = clean.substring(0, dot).trim();
            text = clean.substring(dot + 1).trim();
        } else if (separator > 0 && separator <= 3) {
            key = clean.substring(0, separator).trim();
            text = clean.substring(separator + 1).trim();
        } else {
            return null;
        }
        String normalizedKey = key.toUpperCase(Locale.ROOT);
        if (!normalizedKey.matches("[A-Z]|T|F")) {
            return null;
        }
        boolean correctByMarker = false;
        String normalizedText = text;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("[x]") || lower.contains("(正确)") || lower.contains("[正确]") || lower.contains("（正确）")) {
            correctByMarker = true;
            normalizedText = normalizedText.replace("[x]", "").replace("(正确)", "")
                    .replace("[正确]", "").replace("（正确）", "").trim();
        }
        AdminQuestionUpsertRequest.OptionItem item = new AdminQuestionUpsertRequest.OptionItem();
        item.setKey(normalizedKey);
        item.setText(trimToNull(normalizedText));
        item.setCorrect(false);
        item.setSort(sort);
        OptionParseResult result = new OptionParseResult();
        result.item = item;
        result.correctByMarker = correctByMarker;
        return result;
    }

    private void applyCorrectAnswerKeys(QuestionImportDraft draft) {
        if (draft.answer != null) {
            String[] parts = draft.answer.toUpperCase(Locale.ROOT).replace("，", ",").split("[,、\\s]+");
            for (String part : parts) {
                String key = trimToNull(part);
                if (key != null) {
                    draft.correctKeys.add(key);
                }
            }
        }
        if (draft.correctKeys.isEmpty()) {
            return;
        }
        for (AdminQuestionUpsertRequest.OptionItem option : draft.options) {
            option.setCorrect(draft.correctKeys.contains(option.getKey()));
        }
    }

    private void fillOptionsFromAnswerOnly(QuestionImportDraft draft) {
        if (draft.answer == null) {
            return;
        }
        String upper = draft.answer.toUpperCase(Locale.ROOT);
        if (!"T".equals(upper) && !"F".equals(upper)) {
            return;
        }
        AdminQuestionUpsertRequest.OptionItem optionT = new AdminQuestionUpsertRequest.OptionItem();
        optionT.setKey("T");
        optionT.setText("正确");
        optionT.setCorrect("T".equals(upper));
        optionT.setSort(1);
        AdminQuestionUpsertRequest.OptionItem optionF = new AdminQuestionUpsertRequest.OptionItem();
        optionF.setKey("F");
        optionF.setText("错误");
        optionF.setCorrect("F".equals(upper));
        optionF.setSort(2);
        draft.options = new ArrayList<>(List.of(optionT, optionF));
        draft.correctKeys.clear();
        draft.correctKeys.add(upper);
    }

    private void upsertQuestionByDraft(QuestionImportDraft draft, Long requesterId) {
        AdminQuestionUpsertRequest request = new AdminQuestionUpsertRequest();
        request.setModuleId(draft.moduleId);
        request.setType(draft.type);
        request.setDifficulty(draft.difficulty);
        request.setStem(draft.stem);
        request.setAnalysis(draft.analysis);
        request.setTags(draft.tags);
        request.setStatus(draft.status);
        request.setOptions(draft.options);
        validateQuestionUpsertRequest(request);
        assertModuleExists(request.getModuleId());
        String dedupeStem = request.getStem() == null ? null : request.getStem().trim();
        Question existed = questionMapper.selectOne(new LambdaQueryWrapper<Question>()
                .eq(Question::getModuleId, request.getModuleId())
                .eq(Question::getType, request.getType())
                .eq(Question::getStem, dedupeStem)
                .orderByDesc(Question::getId));
        LocalDateTime now = LocalDateTime.now();
        if (existed == null) {
            Question question = new Question();
            fillQuestionByRequest(question, request);
            question.setCreatedBy(requesterId);
            question.setCreatedAt(now);
            question.setUpdatedAt(now);
            int inserted = questionMapper.insert(question);
            if (inserted != 1 || question.getId() == null) {
                throw new BusinessException(500, "服务器内部错误");
            }
            saveQuestionOptions(question.getId(), request.getOptions());
            return;
        }
        fillQuestionByRequest(existed, request);
        existed.setUpdatedAt(now);
        int updated = questionMapper.updateById(existed);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>().eq(QuestionOption::getQuestionId, existed.getId()));
        saveQuestionOptions(existed.getId(), request.getOptions());
    }

    private AdminQuestionImportResponse.FailedItem buildImportFailedItem(int index, String stem, String reason) {
        AdminQuestionImportResponse.FailedItem item = new AdminQuestionImportResponse.FailedItem();
        item.setIndex(index);
        item.setStem(truncate(stem, 80));
        item.setReason(reason);
        return item;
    }

    private Long parseLongValue(String value) {
        Integer integer = parseIntegerValue(value);
        return integer == null ? null : integer.longValue();
    }

    private Integer parseIntegerValue(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseTypeValue(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "1", "SINGLE", "单选", "单选题" -> 1;
            case "2", "MULTI", "MULTIPLE", "多选", "多选题" -> 2;
            case "3", "JUDGE", "TF", "判断", "判断题" -> 3;
            default -> parseIntegerValue(text);
        };
    }

    private Integer parseDifficultyValue(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "EASY", "简单" -> 1;
            case "2", "MEDIUM", "中等" -> 2;
            case "3", "HARD", "困难" -> 3;
            default -> parseIntegerValue(text);
        };
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    // ── AI 出题 ──

    public GenerateQuestionsResponse generateQuestions(Long requesterId, GenerateQuestionsRequest request) {
        assertAdmin(requesterId);
        assertModuleExists(request.getModuleId());

        QuestionModule module = questionModuleMapper.selectById(request.getModuleId());
        String moduleName = module != null ? module.getName() : "未知模块";
        String categoryName = module != null ? module.getCategoryName() : null;

        int count = request.getCount() != null ? request.getCount() : 5;
        int batchSize = 5;
        int batches = (int) Math.ceil((double) count / batchSize);

        List<CompletableFuture<List<QuestionImportDraft>>> futures = new ArrayList<>();
        for (int i = 0; i < batches; i++) {
            int batchCount = Math.min(batchSize, count - i * batchSize);
            final int batchNo = i + 1;
            CompletableFuture<List<QuestionImportDraft>> future = CompletableFuture.supplyAsync(() -> {
                log.info("AI 生成题目 批次 {}/{}: 请求 {} 题", batchNo, batches, batchCount);
                String generatedJson = deepSeekService.generateQuestions(
                        moduleName, categoryName, batchCount,
                        request.getDifficulty(), request.getType());
                try {
                    return parseAiGeneratedQuestions(generatedJson, request.getModuleId());
                } catch (Exception ex) {
                    log.error("AI 生成题目 JSON 解析失败 (批次 {}): {}", batchNo, ex.getMessage());
                    throw new RuntimeException("批次" + batchNo + "解析失败: " + ex.getMessage());
                }
            }, reviewExecutor);
            futures.add(future);
        }

        List<QuestionImportDraft> allDrafts = new ArrayList<>();
        List<String> batchErrors = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                allDrafts.addAll(futures.get(i).get());
            } catch (Exception ex) {
                Throwable cause = ex.getCause();
                String msg = cause != null ? cause.getMessage() : ex.getMessage();
                log.error("AI 生成题目 批次 {} 失败: {}", i + 1, msg);
                batchErrors.add("批次" + (i + 1) + ": " + msg);
            }
        }

        if (allDrafts.isEmpty()) {
            String detail = batchErrors.isEmpty() ? "所有批次均未返回题目" : String.join("; ", batchErrors);
            throw new BusinessException("AI 生成题目失败: " + detail);
        }

        List<Long> questionIds = transactionTemplate.execute(
                status -> saveGeneratedQuestions(requesterId, allDrafts));

        GenerateQuestionsResponse response = new GenerateQuestionsResponse();
        response.setGeneratedCount(questionIds.size());
        response.setQuestionIds(questionIds);
        return response;
    }

    private List<Long> saveGeneratedQuestions(Long requesterId, List<QuestionImportDraft> drafts) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> questionIds = new ArrayList<>();
        for (QuestionImportDraft draft : drafts) {
            AdminQuestionUpsertRequest upsert = new AdminQuestionUpsertRequest();
            upsert.setModuleId(draft.moduleId);
            upsert.setType(draft.type);
            upsert.setDifficulty(draft.difficulty);
            upsert.setStem(draft.stem);
            upsert.setAnalysis(draft.analysis);
            upsert.setTags(draft.tags);
            upsert.setStatus(2);
            upsert.setOptions(draft.options);

            validateQuestionUpsertRequest(upsert);
            Question question = new Question();
            fillQuestionByRequest(question, upsert);
            question.setStatus(2);
            question.setCreatedBy(requesterId);
            question.setCreatedAt(now);
            question.setUpdatedAt(now);
            int inserted = questionMapper.insert(question);
            if (inserted != 1 || question.getId() == null) {
                throw new BusinessException(500, "服务器内部错误");
            }
            saveQuestionOptions(question.getId(), upsert.getOptions());
            questionIds.add(question.getId());
        }
        return questionIds;
    }

    // ── AI 审题 ──

    public String startBatchReview(Long requesterId) {
        assertAdmin(requesterId);
        List<Question> drafts = questionMapper.selectList(
                new LambdaQueryWrapper<Question>().eq(Question::getStatus, 2));

        String batchId = UUID.randomUUID().toString().substring(0, 8);
        BatchReviewState state = new BatchReviewState();
        state.batchId = batchId;
        state.total = drafts.size();
        state.startedAt = System.currentTimeMillis();
        reviewStates.put(batchId, state);

        if (drafts.isEmpty()) {
            state.completed = true;
            return batchId;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Question draft : drafts) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return reviewAndClassify(draft);
                } catch (Exception ex) {
                    log.warn("审题失败 questionId={}: {}", draft.getId(), ex.getMessage());
                    ReviewDraftQuestionResponse errItem = new ReviewDraftQuestionResponse();
                    errItem.setQuestionId(draft.getId());
                    errItem.setStem(truncateStem(draft.getStem(), 60));
                    errItem.setConfidence(0);
                    errItem.setIssues(List.of("审题失败: " + ex.getMessage()));
                    errItem.setAction("PENDING");
                    return errItem;
                }
            }, reviewExecutor).thenAccept(result -> {
                state.results.add(result);
                state.current.incrementAndGet();
                switch (result.getAction()) {
                    case "PUBLISHED" -> state.published.incrementAndGet();
                    case "DELETED" -> state.deleted.incrementAndGet();
                    case "PENDING" -> state.pending.incrementAndGet();
                    default -> state.errors.incrementAndGet();
                }
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    state.completed = true;
                    // clean up after 5 minutes
                    Executors.newSingleThreadScheduledExecutor().schedule(
                            () -> reviewStates.remove(batchId), 5, java.util.concurrent.TimeUnit.MINUTES);
                });

        return batchId;
    }

    public Map<String, Object> getBatchReviewProgress(Long requesterId, String batchId) {
        assertAdmin(requesterId);
        BatchReviewState state = reviewStates.get(batchId);
        if (state == null) {
            throw new BusinessException("审核批次不存在或已过期");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("batchId", state.batchId);
        result.put("total", state.total);
        result.put("current", state.current.get());
        result.put("published", state.published.get());
        result.put("deleted", state.deleted.get());
        result.put("pending", state.pending.get());
        result.put("errors", state.errors.get());
        result.put("completed", state.completed);
        result.put("results", state.results);
        return result;
    }

    static class BatchReviewState {
        String batchId;
        int total;
        AtomicInteger current = new AtomicInteger(0);
        AtomicInteger published = new AtomicInteger(0);
        AtomicInteger deleted = new AtomicInteger(0);
        AtomicInteger pending = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        volatile boolean completed;
        long startedAt;
        List<ReviewDraftQuestionResponse> results = new CopyOnWriteArrayList<>();
    }

    public ReviewDraftQuestionResponse reviewDraftQuestion(Long requesterId, Long questionId) {
        assertAdmin(requesterId);
        Question draft = questionMapper.selectById(questionId);
        if (draft == null || draft.getStatus() == null || draft.getStatus() != 2) {
            throw new BusinessException("题目不存在或不是草稿状态");
        }
        return reviewAndClassify(draft);
    }

    private ReviewDraftQuestionResponse reviewAndClassify(Question draft) {
        List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, draft.getId())
                        .orderByAsc(QuestionOption::getSort));

        QuestionModule module = questionModuleMapper.selectById(draft.getModuleId());
        String moduleName = module != null ? module.getName() : "未知模块";
        String categoryName = module != null ? module.getCategoryName() : null;

        String questionForReview = buildReviewPrompt(draft, options, moduleName, categoryName);
        ZhipuService.ReviewResult result = zhipuService.reviewQuestion(questionForReview);

        ReviewDraftQuestionResponse response = new ReviewDraftQuestionResponse();
        response.setQuestionId(draft.getId());
        response.setStem(truncateStem(draft.getStem(), 60));
        response.setCorrect(result.isCorrect());
        response.setConfidence(result.getConfidence());
        response.setIssues(result.getIssues());

        if (result.isCorrect() && result.getConfidence() >= 8) {
            draft.setStatus(0);
            draft.setUpdatedAt(LocalDateTime.now());
            questionMapper.updateById(draft);
            response.setAction("PUBLISHED");
        } else if (!result.isCorrect()) {
            questionOptionMapper.delete(
                    new LambdaQueryWrapper<QuestionOption>()
                            .eq(QuestionOption::getQuestionId, draft.getId()));
            questionMapper.deleteById(draft.getId());
            response.setAction("DELETED");
        } else {
            response.setAction("PENDING");
        }
        return response;
    }

    private String buildReviewPrompt(Question draft, List<QuestionOption> options,
                                     String moduleName, String categoryName) {
        StringBuilder sb = new StringBuilder();
        if (categoryName != null && !categoryName.isBlank()) {
            sb.append("分类：").append(categoryName).append("\n");
        }
        sb.append("模块：").append(moduleName).append("\n");
        String typeLabel = draft.getType() == 1 ? "单选题" : draft.getType() == 2 ? "多选题" : "判断题";
        sb.append("题型：").append(typeLabel).append("\n");
        String diffLabel = draft.getDifficulty() == 1 ? "简单"
                : draft.getDifficulty() == 2 ? "中等" : "困难";
        sb.append("难度：").append(diffLabel).append("\n");
        sb.append("题干：").append(draft.getStem()).append("\n");
        sb.append("选项：\n");
        for (QuestionOption opt : options) {
            sb.append("  ").append(opt.getOptionKey()).append(". ").append(opt.getOptionText());
            if (opt.getIsCorrect() != null && opt.getIsCorrect() == 1) {
                sb.append(" [正确答案]");
            }
            sb.append("\n");
        }
        if (draft.getAnalysis() != null && !draft.getAnalysis().isBlank()) {
            sb.append("解析：").append(draft.getAnalysis()).append("\n");
        }
        if (draft.getTags() != null && !draft.getTags().isBlank()) {
            sb.append("标签：").append(draft.getTags()).append("\n");
        }
        return sb.toString();
    }

    @Transactional
    public void publishDraftQuestion(Long requesterId, Long questionId) {
        assertAdmin(requesterId);
        Question question = questionMapper.selectById(questionId);
        if (question == null || question.getStatus() == null || question.getStatus() != 2) {
            throw new BusinessException("题目不存在或不是草稿状态");
        }
        question.setStatus(0);
        question.setUpdatedAt(LocalDateTime.now());
        questionMapper.updateById(question);
    }

    public Map<String, Object> listDraftQuestions(Long requesterId, Integer page, Integer size) {
        assertAdmin(requesterId);
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, 2).orderByDesc(Question::getId);

        List<Question> records;
        int total;
        if (page == null || size == null) {
            records = questionMapper.selectList(wrapper);
            total = records.size();
        } else {
            int pageNum = Math.max(page, 1);
            int pageSize = Math.max(size, 1);
            Page<Question> mpPage = new Page<>(pageNum, pageSize);
            Page<Question> result = questionMapper.selectPage(mpPage, wrapper);
            records = result.getRecords();
            total = (int) result.getTotal();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Question q : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", q.getId());
            item.put("moduleId", q.getModuleId());
            item.put("type", q.getType());
            item.put("difficulty", q.getDifficulty());
            item.put("stem", q.getStem());
            item.put("analysis", q.getAnalysis());
            item.put("tags", q.getTags());
            item.put("status", q.getStatus());
            item.put("updatedAt", q.getUpdatedAt());

            List<QuestionOption> options = questionOptionMapper.selectList(
                    new LambdaQueryWrapper<QuestionOption>()
                            .eq(QuestionOption::getQuestionId, q.getId())
                            .orderByAsc(QuestionOption::getSort));
            List<Map<String, Object>> optionList = new ArrayList<>();
            for (QuestionOption opt : options) {
                Map<String, Object> om = new HashMap<>();
                om.put("key", opt.getOptionKey());
                om.put("text", opt.getOptionText());
                om.put("correct", opt.getIsCorrect() != null && opt.getIsCorrect() == 1);
                om.put("sort", opt.getSort());
                optionList.add(om);
            }
            item.put("options", optionList);
            list.add(item);
        }

        Map<String, Object> pageData = new HashMap<>();
        pageData.put("list", list);
        pageData.put("total", total);
        return pageData;
    }

    private List<QuestionImportDraft> parseAiGeneratedQuestions(String json, Long moduleId) {
        ObjectMapper mapper = new ObjectMapper();
        List<?> rawList;
        try {
            rawList = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            try {
                int start = json.indexOf('[');
                int end = json.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    rawList = mapper.readValue(json.substring(start, end + 1),
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                } else {
                    throw new BusinessException("AI 生成的题目 JSON 解析失败");
                }
            } catch (BusinessException bx) {
                throw bx;
            } catch (Exception ex2) {
                throw new BusinessException("AI 生成的题目 JSON 解析失败");
            }
        }

        List<QuestionImportDraft> drafts = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            QuestionImportDraft draft = new QuestionImportDraft();
            draft.moduleId = moduleId;

            Object typeObj = map.get("type");
            draft.type = typeObj instanceof Number n ? n.intValue() : 1;

            Object diffObj = map.get("difficulty");
            draft.difficulty = diffObj instanceof Number n ? n.intValue() : 2;

            Object stemObj = map.get("stem");
            draft.stem = stemObj != null ? stemObj.toString().trim() : "";

            Object analysisObj = map.get("analysis");
            draft.analysis = analysisObj != null ? analysisObj.toString().trim() : null;

            Object tagsObj = map.get("tags");
            draft.tags = tagsObj != null ? tagsObj.toString().trim() : null;

            Object optionsObj = map.get("options");
            List<AdminQuestionUpsertRequest.OptionItem> optionItems = new ArrayList<>();
            if (optionsObj instanceof List<?> optList) {
                char key = 'A';
                for (Object optItem : optList) {
                    if (optItem instanceof Map<?, ?> optMap) {
                        AdminQuestionUpsertRequest.OptionItem oi = new AdminQuestionUpsertRequest.OptionItem();
                        Object optKeyObj = optMap.get("key");
                        oi.setKey(optKeyObj != null ? optKeyObj.toString() : String.valueOf(key));
                        Object optTextObj = optMap.get("text");
                        oi.setText(optTextObj != null ? optTextObj.toString() : "");
                        Object correctObj = optMap.get("correct");
                        oi.setCorrect(correctObj instanceof Boolean b ? b : false);
                        oi.setSort(optionItems.size() + 1);
                        optionItems.add(oi);
                        key++;
                    }
                }
            }
            // Normalize true/false question options: force keys to T/F
            if (draft.type == 3 && optionItems.size() == 2) {
                boolean firstCorrect = Boolean.TRUE.equals(optionItems.get(0).getCorrect());
                boolean secondCorrect = Boolean.TRUE.equals(optionItems.get(1).getCorrect());
                optionItems.get(0).setKey("T");
                optionItems.get(0).setText("正确");
                optionItems.get(0).setCorrect(firstCorrect || (!firstCorrect && !secondCorrect));
                optionItems.get(1).setKey("F");
                optionItems.get(1).setText("错误");
                optionItems.get(1).setCorrect(secondCorrect && !optionItems.get(0).getCorrect());
            }
            draft.options = optionItems;
            drafts.add(draft);
        }
        if (drafts.isEmpty()) {
            throw new BusinessException("AI 未生成有效题目，请调整参数后重试");
        }
        return drafts;
    }

    private String truncateStem(String stem, int maxLen) {
        if (stem == null) {
            return "";
        }
        String trimmed = stem.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen) + "...";
    }

    private static class QuestionImportDraft {
        private Long moduleId;
        private Integer type;
        private Integer difficulty;
        private String stem;
        private String analysis;
        private String tags;
        private Integer status;
        private String answer;
        private List<AdminQuestionUpsertRequest.OptionItem> options = new ArrayList<>();
        private Set<String> correctKeys = new HashSet<>();
    }

    private static class OptionParseResult {
        private AdminQuestionUpsertRequest.OptionItem item;
        private boolean correctByMarker;
    }
}
