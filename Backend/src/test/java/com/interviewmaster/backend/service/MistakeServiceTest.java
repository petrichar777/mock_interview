package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.MistakeMapper;
import com.interviewmaster.backend.mapper.PracticeMapper;
import com.interviewmaster.backend.mapper.QuestionMapper;
import com.interviewmaster.backend.mapper.QuestionModuleMapper;
import com.interviewmaster.backend.mapper.QuestionOptionMapper;
import com.interviewmaster.backend.model.dto.response.MistakeDetailResponse;
import com.interviewmaster.backend.model.dto.response.MistakeListResponse;
import com.interviewmaster.backend.model.dto.response.MistakeRandomResponse;
import com.interviewmaster.backend.model.dto.response.MistakeRemoveResponse;
import com.interviewmaster.backend.model.entity.Question;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.QuestionOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class MistakeServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionModuleMapper questionModuleMapper;

    @Mock
    private QuestionOptionMapper questionOptionMapper;

    @Mock
    private PracticeMapper practiceMapper;

    @Mock
    private MistakeMapper mistakeMapper;

    @Mock
    private ObjectMapper objectMapper;

    private MistakeService mistakeService;

    @BeforeEach
    void setUp() {
        mistakeService = new MistakeService(stringRedisTemplate, questionMapper, questionModuleMapper, questionOptionMapper, practiceMapper, mistakeMapper, objectMapper);
    }

    @Test
    void list_mistakes_success() {
        Question question1 = new Question();
        question1.setId(101L);
        question1.setModuleId(1L);
        question1.setType(1);
        question1.setDifficulty(2);
        question1.setStem("题目1");
        question1.setStatus(0);
        Question question2 = new Question();
        question2.setId(102L);
        question2.setModuleId(2L);
        question2.setType(2);
        question2.setDifficulty(3);
        question2.setStem("题目2");
        question2.setStatus(0);
        QuestionModule module1 = new QuestionModule();
        module1.setId(1L);
        module1.setName("异常处理");
        QuestionModule module2 = new QuestionModule();
        module2.setId(2L);
        module2.setName("多线程");
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:mistakes:9")).thenReturn(Set.of("101", "102"));
        when(practiceMapper.countMistakeQuestions(org.mockito.ArgumentMatchers.eq(9L), any(), org.mockito.ArgumentMatchers.eq(null)))
                .thenReturn(2);
        when(practiceMapper.listMistakeQuestionIdsPaged(org.mockito.ArgumentMatchers.eq(9L), any(), org.mockito.ArgumentMatchers.eq(null),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(null)))
                .thenReturn(List.of(102L, 101L));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question1, question2));
        when(questionModuleMapper.selectBatchIds(any())).thenReturn(List.of(module1, module2));
        when(practiceMapper.listLastWrongAtByQuestionIds(org.mockito.ArgumentMatchers.eq(9L), any())).thenReturn(List.of(
                Map.of("questionId", 101L, "lastWrongAt", LocalDateTime.parse("2026-03-22T10:00:00")),
                Map.of("questionId", 102L, "lastWrongAt", LocalDateTime.parse("2026-03-22T11:00:00"))
        ));

        MistakeListResponse response = mistakeService.listMistakes(9L, null, 1, 10, null);

        assertNotNull(response);
        assertEquals(2, response.getTotal());
        assertEquals(2, response.getList().size());
        assertEquals(102L, response.getList().get(0).getQuestionId());
        assertEquals("多线程", response.getList().get(0).getModuleName());
    }

    @Test
    void list_mistakes_invalid_page() {
        BusinessException ex = assertThrows(BusinessException.class, () -> mistakeService.listMistakes(9L, null, 0, 10, null));

        assertEquals("参数错误：page 必须大于等于 1", ex.getMessage());
    }

    @Test
    void random_mistakes_success() {
        Question question1 = new Question();
        question1.setId(101L);
        question1.setModuleId(1L);
        question1.setStatus(0);
        Question question2 = new Question();
        question2.setId(102L);
        question2.setModuleId(1L);
        question2.setStatus(0);
        Question question3 = new Question();
        question3.setId(103L);
        question3.setModuleId(2L);
        question3.setStatus(0);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:mistakes:9")).thenReturn(Set.of("101", "102", "103"));
        when(questionMapper.selectList(any())).thenReturn(List.of(question1, question2));

        MistakeRandomResponse response = mistakeService.randomMistakes(9L, 2, 1L);

        assertNotNull(response);
        assertEquals(2, response.getQuestionIds().size());
        assertTrue(response.getQuestionIds().contains(101L));
        assertTrue(response.getQuestionIds().contains(102L));
    }

    @Test
    void remove_mistake_success() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.remove("user:mistakes:9", "101")).thenReturn(1L);
        when(setOperations.size("user:mistakes:9")).thenReturn(4L);

        MistakeRemoveResponse response = mistakeService.removeMistake(9L, 101L);

        assertTrue(response.getRemoved());
        assertEquals(4, response.getMistakeCount());
    }

    @Test
    void get_mistake_detail_success() {
        Question question = new Question();
        question.setId(101L);
        question.setModuleId(1L);
        question.setType(2);
        question.setDifficulty(3);
        question.setStem("题目1");
        question.setAnalysis("第一段。第二段。");
        question.setTags("并发,锁");
        question.setStatus(0);
        QuestionModule module = new QuestionModule();
        module.setId(1L);
        module.setName("异常处理");
        QuestionOption optionA = new QuestionOption();
        optionA.setQuestionId(101L);
        optionA.setOptionKey("A");
        optionA.setOptionText("选项A");
        optionA.setIsCorrect(1);
        optionA.setSort(1);
        QuestionOption optionB = new QuestionOption();
        optionB.setQuestionId(101L);
        optionB.setOptionKey("B");
        optionB.setOptionText("选项B");
        optionB.setIsCorrect(0);
        optionB.setSort(2);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("user:mistakes:9", "101")).thenReturn(true);
        when(questionMapper.selectById(101L)).thenReturn(question);
        when(questionModuleMapper.selectById(1L)).thenReturn(module);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(optionA, optionB));
        when(practiceMapper.getLastWrongAt(9L, 101L)).thenReturn(LocalDateTime.parse("2026-03-22T12:00:00"));
        when(practiceMapper.getLastWrongAnswers(9L, 101L)).thenReturn("B");

        MistakeDetailResponse response = mistakeService.getMistakeDetail(9L, 101L);

        assertEquals(101L, response.getQuestionId());
        assertEquals("异常处理", response.getModuleName());
        assertEquals(List.of("A"), response.getCorrectAnswers());
        assertEquals(List.of("第一段", "第二段"), response.getExplanations());
        assertEquals(List.of("并发", "锁"), response.getTags());
        assertEquals(List.of("B"), response.getLastWrongAnswers());
    }

    @Test
    void get_mistake_detail_not_found() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("user:mistakes:9", "101")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> mistakeService.getMistakeDetail(9L, 101L));

        assertEquals(40401, ex.getCode());
    }
}
