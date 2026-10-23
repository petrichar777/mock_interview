package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.CategoryMapper;
import com.interviewmaster.backend.mapper.DirectionMapper;
import com.interviewmaster.backend.mapper.QuestionModuleMapper;
import com.interviewmaster.backend.mapper.QuestionMapper;
import com.interviewmaster.backend.mapper.QuestionOptionMapper;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.dto.request.AdminModuleUpsertRequest;
import com.interviewmaster.backend.model.dto.request.AdminQuestionUpsertRequest;
import com.interviewmaster.backend.model.dto.request.RegisterRequest;
import com.interviewmaster.backend.model.dto.response.AdminQuestionDetailResponse;
import com.interviewmaster.backend.model.dto.response.AdminQuestionImportResponse;
import com.interviewmaster.backend.model.entity.Category;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.Question;
import com.interviewmaster.backend.model.entity.QuestionOption;
import com.interviewmaster.backend.model.entity.User;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private QuestionModuleMapper questionModuleMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionOptionMapper questionOptionMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private DirectionMapper directionMapper;

    @Mock
    private DeepSeekService deepSeekService;

    @Mock
    private ZhipuService zhipuService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        adminService = new AdminService(userMapper, categoryMapper, directionMapper,
                questionModuleMapper, questionMapper, questionOptionMapper,
                passwordEncoder, deepSeekService, zhipuService, transactionTemplate);
    }

    @Test
    void register_admin_success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("admin1");
        request.setEmail("admin1@example.com");
        request.setPassword("12345678");
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(11L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        Long userId = adminService.registerAdmin(request);

        assertEquals(11L, userId);
    }

    @Test
    void list_users_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        User user1 = new User();
        user1.setId(2L);
        user1.setUsername("jack");
        user1.setEmail("jack@example.com");
        user1.setRole("USER");
        user1.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(userMapper.selectCount(any())).thenReturn(1L);
        doAnswer(invocation -> {
            Page<User> page = invocation.getArgument(0);
            page.setRecords(List.of(user1));
            page.setTotal(1);
            return page;
        }).when(userMapper).selectPage(any(Page.class), any(Wrapper.class));

        Map<String, Object> data = adminService.listUsers(1L, "jack", "USER", 0, 1, 10);

        assertEquals(1, data.get("page"));
        assertEquals(10, data.get("size"));
        assertEquals(1L, data.get("total"));
    }

    @Test
    void list_users_forbidden_when_not_admin() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("USER");
        when(userMapper.selectById(1L)).thenReturn(requester);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.listUsers(1L, null, null, null, 1, 10));

        assertEquals(403, ex.getCode());
    }

    @Test
    void update_user_status_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        User target = new User();
        target.setId(2L);
        target.setRole("USER");
        target.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        adminService.updateUserStatus(1L, 2L, 1);

        assertEquals(1, target.getStatus());
    }

    @Test
    void update_user_status_block_disable_admin() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        User target = new User();
        target.setId(3L);
        target.setRole("ADMIN");
        target.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(userMapper.selectById(3L)).thenReturn(target);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.updateUserStatus(1L, 3L, 1));

        assertEquals("管理员账号不允许被禁用", ex.getMessage());
    }

    @Test
    void list_modules_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        QuestionModule module = new QuestionModule();
        module.setId(8L);
        module.setCategoryId(1L);
        module.setCategoryName("后端");
        module.setName("Spring");
        module.setStatus(0);
        module.setSort(1);
        Category category = new Category();
        category.setId(1L);
        category.setCode("backend");
        category.setName("后端");
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(directionMapper.selectList(any())).thenReturn(List.of());
        when(questionModuleMapper.selectList(any())).thenReturn(List.of(module));

        Map<String, Object> data = adminService.listModules(1L, null, "后端", 0);

        List<?> list = (List<?>) data.get("list");
        assertEquals(1, list.size());
    }

    @Test
    void create_module_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        Category category = new Category();
        category.setId(1L);
        category.setCode("backend");
        category.setName("后端");
        category.setStatus(0);
        AdminModuleUpsertRequest request = new AdminModuleUpsertRequest();
        request.setCategoryId(1L);
        request.setName("Spring Boot");
        request.setDescription("Java后端模块");
        request.setSort(2);
        request.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(categoryMapper.selectById(1L)).thenReturn(category);
        doAnswer(invocation -> {
            QuestionModule module = invocation.getArgument(0);
            module.setId(21L);
            return 1;
        }).when(questionModuleMapper).insert(any(QuestionModule.class));

        Long moduleId = adminService.createModule(1L, request);

        assertEquals(21L, moduleId);
    }

    @Test
    void list_questions_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        Question question = new Question();
        question.setId(9L);
        question.setModuleId(8L);
        question.setStem("Spring 事务传播机制有哪些？");
        question.setType(1);
        question.setDifficulty(2);
        question.setStatus(0);
        QuestionModule module = new QuestionModule();
        module.setId(8L);
        module.setName("Spring");
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(questionMapper.selectCount(any())).thenReturn(1L);
        doAnswer(invocation -> {
            Page<Question> page = invocation.getArgument(0);
            page.setRecords(List.of(question));
            page.setTotal(1);
            return page;
        }).when(questionMapper).selectPage(any(Page.class), any(Wrapper.class));
        when(questionModuleMapper.selectBatchIds(any())).thenReturn(List.of(module));

        Map<String, Object> data = adminService.listQuestions(1L, "事务", 8L, 1, 2, 0, 1, 10);

        assertEquals(1L, data.get("total"));
        assertNotNull(data.get("list"));
    }

    @Test
    void get_question_detail_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        Question question = new Question();
        question.setId(10L);
        question.setModuleId(8L);
        question.setType(1);
        question.setDifficulty(2);
        question.setStem("Java 内存模型是什么？");
        question.setAnalysis("关注可见性与有序性");
        question.setTags("JVM,并发");
        question.setStatus(0);
        QuestionOption option = new QuestionOption();
        option.setQuestionId(10L);
        option.setOptionKey("A");
        option.setOptionText("主内存与工作内存");
        option.setIsCorrect(1);
        option.setSort(1);
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(questionMapper.selectById(10L)).thenReturn(question);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(option));

        AdminQuestionDetailResponse response = adminService.getQuestionDetail(1L, 10L);

        assertEquals(10L, response.getId());
        assertEquals(1, response.getOptions().size());
        assertEquals(true, response.getOptions().get(0).getCorrect());
    }

    @Test
    void create_question_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        QuestionModule module = new QuestionModule();
        module.setId(8L);
        AdminQuestionUpsertRequest request = new AdminQuestionUpsertRequest();
        request.setModuleId(8L);
        request.setType(1);
        request.setDifficulty(2);
        request.setStem("下面哪项是 Spring Bean 生命周期回调？");
        request.setAnalysis("关注初始化回调顺序");
        request.setTags("Spring,基础");
        request.setStatus(0);
        AdminQuestionUpsertRequest.OptionItem optionA = new AdminQuestionUpsertRequest.OptionItem();
        optionA.setKey("A");
        optionA.setText("afterPropertiesSet");
        optionA.setCorrect(true);
        optionA.setSort(1);
        AdminQuestionUpsertRequest.OptionItem optionB = new AdminQuestionUpsertRequest.OptionItem();
        optionB.setKey("B");
        optionB.setText("destroyNow");
        optionB.setCorrect(false);
        optionB.setSort(2);
        request.setOptions(List.of(optionA, optionB));
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(questionModuleMapper.selectById(8L)).thenReturn(module);
        doAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(30L);
            return 1;
        }).when(questionMapper).insert(any(Question.class));
        when(questionOptionMapper.insert(any(QuestionOption.class))).thenReturn(1);

        Long questionId = adminService.createQuestion(1L, request);

        assertEquals(30L, questionId);
    }

    @Test
    void delete_question_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        Question question = new Question();
        question.setId(12L);
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(questionMapper.selectById(12L)).thenReturn(question);
        when(questionOptionMapper.delete(any())).thenReturn(2);
        when(questionMapper.deleteById(12L)).thenReturn(1);

        adminService.deleteQuestion(1L, 12L);

        assertEquals(1, questionMapper.deleteById(12L));
    }

    @Test
    void update_question_status_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        Question question = new Question();
        question.setId(15L);
        question.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(questionMapper.selectById(15L)).thenReturn(question);
        when(questionMapper.updateById(any(Question.class))).thenReturn(1);

        adminService.updateQuestionStatus(1L, 15L, 0);

        assertEquals(0, question.getStatus());
    }

    @Test
    void import_questions_success() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        QuestionModule module = new QuestionModule();
        module.setId(8L);
        String markdown = """
                ### Q1
                moduleId: 8
                type: 单选
                difficulty: 中等
                stem: JVM 哪一项属于垃圾回收器？
                analysis: 关注回收器实现
                tags: JVM,GC
                options:
                - A. G1（正确）
                - B. IOC
                ---
                """;
        MockMultipartFile file = new MockMultipartFile("file", "questions.md", "text/markdown", markdown.getBytes());
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(questionModuleMapper.selectById(8L)).thenReturn(module);
        when(questionMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(101L);
            return 1;
        }).when(questionMapper).insert(any(Question.class));
        when(questionOptionMapper.insert(any(QuestionOption.class))).thenReturn(1);

        AdminQuestionImportResponse response = adminService.importQuestions(1L, file);

        assertEquals(1, response.getTotal());
        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
    }

    @Test
    void import_questions_upsert_existing() {
        User requester = new User();
        requester.setId(1L);
        requester.setRole("ADMIN");
        QuestionModule module = new QuestionModule();
        module.setId(8L);
        Question existed = new Question();
        existed.setId(44L);
        existed.setModuleId(8L);
        existed.setType(1);
        existed.setStem("JVM 哪一项属于垃圾回收器？");
        String markdown = """
                moduleId: 8
                type: 1
                difficulty: 2
                stem: JVM 哪一项属于垃圾回收器？
                answer: A
                options:
                - A. G1（正确）
                - B. IOC
                ---
                """;
        MockMultipartFile file = new MockMultipartFile("file", "questions.md", "text/markdown", markdown.getBytes());
        when(userMapper.selectById(1L)).thenReturn(requester);
        when(questionModuleMapper.selectById(8L)).thenReturn(module);
        when(questionMapper.selectOne(any())).thenReturn(existed);
        when(questionMapper.updateById(any(Question.class))).thenReturn(1);
        when(questionOptionMapper.delete(any())).thenReturn(2);
        when(questionOptionMapper.insert(any(QuestionOption.class))).thenReturn(1);

        AdminQuestionImportResponse response = adminService.importQuestions(1L, file);

        assertEquals(1, response.getSuccessCount());
        verify(questionMapper, times(0)).insert(any(Question.class));
        verify(questionMapper, times(1)).updateById(any(Question.class));
    }
}
