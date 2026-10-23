package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
import com.interviewmaster.backend.model.entity.Question;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.QuestionOption;
import com.interviewmaster.backend.mapper.QuestionOptionMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock
    private QuestionModuleMapper questionModuleMapper;

    @Mock
    private PracticeMapper practiceMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionOptionMapper questionOptionMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MistakeMapper mistakeMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private DirectionMapper directionMapper;

    private PracticeService practiceService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        practiceService = new PracticeService(questionModuleMapper, questionMapper, questionOptionMapper, practiceMapper, mistakeMapper, categoryMapper, directionMapper, stringRedisTemplate);
    }

    @Test
    void list_modules_success_with_category_filter() {
        Category backendCat = new Category();
        backendCat.setId(1L);
        backendCat.setCode("backend");
        backendCat.setName("后端");
        Category frontendCat = new Category();
        frontendCat.setId(2L);
        frontendCat.setCode("frontend");
        frontendCat.setName("前端");
        Category algorithmCat = new Category();
        algorithmCat.setId(3L);
        algorithmCat.setCode("algorithm");
        algorithmCat.setName("算法");
        when(categoryMapper.selectList(any())).thenReturn(List.of(backendCat, frontendCat, algorithmCat));
        when(directionMapper.selectList(any())).thenReturn(List.of());

        QuestionModule backend = new QuestionModule();
        backend.setId(1L);
        backend.setCategoryId(1L);
        backend.setCategoryName("后端");
        backend.setName("异常处理");
        backend.setDescription("异常模块");
        backend.setStatus(0);
        backend.setSort(1);
        QuestionModule frontend = new QuestionModule();
        frontend.setId(7L);
        frontend.setCategoryId(2L);
        frontend.setCategoryName("前端");
        frontend.setName("JavaScript基础");
        frontend.setDescription("前端模块");
        frontend.setStatus(0);
        frontend.setSort(2);
        when(questionModuleMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(backend, frontend));
        when(practiceMapper.countAvailableQuestionByModuleIds(List.of(1L, 7L))).thenReturn(List.of(
                Map.of("moduleId", 1L, "total", 20L),
                Map.of("moduleId", 7L, "total", 10L)
        ));
        when(practiceMapper.countDoneQuestionByUserAndModuleIds(9L, List.of(1L, 7L))).thenReturn(List.of(
                Map.of("moduleId", 1L, "total", 5L)
        ));
        when(practiceMapper.listQuestionTypeRowsByModuleIds(List.of(1L, 7L))).thenReturn(List.of(
                Map.of("moduleId", 1L, "type", 1),
                Map.of("moduleId", 1L, "type", 2),
                Map.of("moduleId", 1L, "type", 3)
        ));
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size("user:mistakes:9")).thenReturn(3L);

        PracticeModuleListResponse response = practiceService.listModules(9L, "backend");

        assertNotNull(response);
        assertEquals(4, response.getCategories().size());
        assertEquals(1, response.getList().size());
        assertEquals("backend", response.getList().get(0).getCategoryCode());
        assertEquals(25, response.getList().get(0).getProgressPercent());
        assertEquals(true, response.getMistakeEntry().getEnabled());
        assertEquals(3, response.getMistakeEntry().getMistakeCount());
    }

    @Test
    void list_modules_invalid_category() {
        Category backendCat = new Category();
        backendCat.setCode("backend");
        Category frontendCat = new Category();
        frontendCat.setCode("frontend");
        Category algorithmCat = new Category();
        algorithmCat.setCode("algorithm");
        when(categoryMapper.selectList(any())).thenReturn(List.of(backendCat, frontendCat, algorithmCat));
        when(directionMapper.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> practiceService.listModules(9L, "java"));

        assertEquals("参数错误：category 不支持该分类", ex.getMessage());
    }

    @Test
    void list_modules_unauthorized() {
        BusinessException ex = assertThrows(BusinessException.class, () -> practiceService.listModules(null, "all"));

        assertEquals(401, ex.getCode());
    }

    @Test
    void get_next_question_normal_success() {
        QuestionModule module = new QuestionModule();
        module.setId(2L);
        module.setName("多线程");
        module.setStatus(0);
        Question q1 = new Question();
        q1.setId(11L);
        q1.setModuleId(2L);
        q1.setType(1);
        q1.setDifficulty(2);
        q1.setStem("题目1");
        q1.setStatus(0);
        Question q2 = new Question();
        q2.setId(12L);
        q2.setModuleId(2L);
        q2.setType(2);
        q2.setDifficulty(3);
        q2.setStem("题目2");
        q2.setStatus(0);
        QuestionOption optionA = new QuestionOption();
        optionA.setQuestionId(12L);
        optionA.setOptionKey("A");
        optionA.setOptionText("选项A");
        optionA.setSort(1);
        QuestionOption optionB = new QuestionOption();
        optionB.setQuestionId(12L);
        optionB.setOptionKey("B");
        optionB.setOptionText("选项B");
        optionB.setSort(2);
        when(questionModuleMapper.selectById(2L)).thenReturn(module);
        when(questionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(q1, q2));
        when(practiceMapper.listDoneQuestionIdsByUserAndModule(9L, 2L)).thenReturn(List.of(11L));
        when(questionOptionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(optionA, optionB));

        PracticeNextQuestionResponse response = practiceService.getNextQuestion(9L, 2L, "normal");

        assertEquals(12L, response.getQuestionId());
        assertEquals("多选题", response.getTypeText());
        assertEquals("困难", response.getDifficultyText());
        assertEquals(2, response.getProgressCurrent());
        assertEquals(2, response.getProgressTotal());
        assertEquals(100, response.getProgressPercent());
        assertTrue(response.getHasPrev());
        assertFalse(response.getHasNext());
        assertEquals(2, response.getOptions().size());
    }

    @Test
    void get_next_question_mistake_without_data() {
        QuestionModule module = new QuestionModule();
        module.setId(2L);
        module.setName("多线程");
        module.setStatus(0);
        Question q1 = new Question();
        q1.setId(11L);
        q1.setModuleId(2L);
        q1.setType(1);
        q1.setDifficulty(2);
        q1.setStem("题目1");
        q1.setStatus(0);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:mistakes:9")).thenReturn(Set.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> practiceService.getNextQuestion(9L, 2L, "mistake"));

        assertEquals("当前暂无可练习题目", ex.getMessage());
    }

    @Test
    void get_prev_question_success() {
        QuestionModule module = new QuestionModule();
        module.setId(2L);
        module.setName("多线程");
        module.setStatus(0);
        Question q1 = new Question();
        q1.setId(11L);
        q1.setModuleId(2L);
        q1.setType(1);
        q1.setDifficulty(2);
        q1.setStem("题目1");
        q1.setStatus(0);
        Question q2 = new Question();
        q2.setId(12L);
        q2.setModuleId(2L);
        q2.setType(2);
        q2.setDifficulty(3);
        q2.setStem("题目2");
        q2.setStatus(0);
        QuestionOption optionA = new QuestionOption();
        optionA.setQuestionId(11L);
        optionA.setOptionKey("A");
        optionA.setOptionText("选项A");
        optionA.setSort(1);
        when(questionModuleMapper.selectById(2L)).thenReturn(module);
        when(questionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(q1, q2));
        when(practiceMapper.listDoneQuestionIdsByUserAndModule(9L, 2L)).thenReturn(List.of(11L));
        when(questionOptionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(optionA));

        PracticeNextQuestionResponse response = practiceService.getPrevQuestion(9L, 2L, "normal");

        assertEquals(11L, response.getQuestionId());
        assertFalse(response.getHasPrev());
        assertTrue(response.getHasNext());
    }

    @Test
    void submit_answer_success_and_add_mistake() {
        QuestionModule module = new QuestionModule();
        module.setId(2L);
        module.setName("多线程");
        module.setStatus(0);
        Question question = new Question();
        question.setId(12L);
        question.setModuleId(2L);
        question.setType(1);
        question.setDifficulty(2);
        question.setStem("题目2");
        question.setAnalysis("第一点说明。第二点说明。");
        question.setTags("并发,锁");
        question.setStatus(0);
        QuestionOption optionA = new QuestionOption();
        optionA.setQuestionId(12L);
        optionA.setOptionKey("A");
        optionA.setOptionText("选项A");
        optionA.setIsCorrect(1);
        optionA.setSort(1);
        QuestionOption optionB = new QuestionOption();
        optionB.setQuestionId(12L);
        optionB.setOptionKey("B");
        optionB.setOptionText("选项B");
        optionB.setIsCorrect(0);
        optionB.setSort(2);
        PracticeSubmitAnswerRequest request = new PracticeSubmitAnswerRequest();
        request.setAnswers(List.of("B"));
        request.setMode("normal");
        request.setTimeCostMs(90000L);
        when(questionMapper.selectById(12L)).thenReturn(question);
        when(questionModuleMapper.selectById(2L)).thenReturn(module);
        when(questionOptionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(optionA, optionB));
        when(practiceMapper.insertPracticeRecord(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq("B"),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(90000L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add("user:mistakes:9", "12")).thenReturn(1L);
        when(questionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(question));
        when(practiceMapper.listDoneQuestionIdsByUserAndModule(9L, 2L)).thenReturn(List.of(12L));

        PracticeSubmitAnswerResponse response = practiceService.submitAnswer(9L, 12L, request);

        assertFalse(response.getCorrect());
        assertEquals(List.of("A"), response.getCorrectAnswers());
        assertEquals(2, response.getExplanations().size());
        assertEquals(List.of("并发", "锁"), response.getTags());
        assertTrue(response.getAutoAddedToMistakes());
        assertFalse(response.getHasNext());
        assertTrue(response.getFinished());
    }

    @Test
    void add_mistake_success() {
        Question question = new Question();
        question.setId(12L);
        question.setStatus(0);
        when(questionMapper.selectById(12L)).thenReturn(question);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add("user:mistakes:9", "12")).thenReturn(1L);
        when(setOperations.size("user:mistakes:9")).thenReturn(5L);

        PracticeAddMistakeResponse response = practiceService.addMistake(9L, 12L);

        assertTrue(response.getAdded());
        assertEquals(5, response.getMistakeCount());
    }
}
