package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.InterviewMessageMapper;
import com.interviewmaster.backend.mapper.InterviewSessionMapper;
import com.interviewmaster.backend.mapper.ResumeOptimizationMapper;
import com.interviewmaster.backend.mapper.UserMapper;
import org.springframework.context.ApplicationContext;
import com.interviewmaster.backend.util.RedisUtil;
import com.interviewmaster.backend.model.dto.request.CreateInterviewSessionRequest;
import com.interviewmaster.backend.model.dto.request.FinishInterviewRequest;
import com.interviewmaster.backend.model.dto.request.SubmitInterviewAnswerRequest;
import com.interviewmaster.backend.model.dto.response.CreateInterviewSessionResponse;
import com.interviewmaster.backend.model.dto.response.FinishInterviewResponse;
import com.interviewmaster.backend.model.dto.response.InterviewSessionDetailResponse;
import com.interviewmaster.backend.model.dto.response.InterviewSessionMessageResponse;
import com.interviewmaster.backend.model.dto.response.SubmitInterviewAnswerResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    @Mock
    private InterviewMessageMapper interviewMessageMapper;

    @Mock
    private DeepSeekService deepSeekService;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private CheckinService checkinService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ResumeOptimizationMapper resumeOptimizationMapper;

    @Mock
    private ApplicationContext applicationContext;

    private InterviewService interviewService;

    @BeforeEach
    void setUp() {
        lenient().when(checkinService.getContinuousDays(anyLong())).thenReturn(0);
        interviewService = new InterviewService(stringRedisTemplate, interviewSessionMapper, interviewMessageMapper, deepSeekService, new ObjectMapper(), redisUtil, checkinService, userMapper, resumeOptimizationMapper, applicationContext);
    }

    @Test
    void create_session_comprehensive_success() {
        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest();
        request.setJobTitle("Java 后端开发实习生");
        request.setJdText("熟悉 Java 基础和 Spring");
        request.setExtractedResumeText("有后端项目经验");
        request.setMode("COMPREHENSIVE");
        request.setStyle("GUIDED");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(valueOperations.increment(anyString())).thenReturn(2L);
        when(valueOperations.increment("interview:seq:session")).thenReturn(101L);
        when(valueOperations.increment("interview:seq:message")).thenReturn(501L);

        CreateInterviewSessionResponse response = interviewService.createSession(1L, request);

        assertEquals(101L, response.getSessionId());
        assertEquals("COMPREHENSIVE", response.getMode());
        assertEquals("IN_PROGRESS", response.getStatus());
        assertNull(response.getAlgoFlow());
        assertNotNull(response.getFirstMessage());
        assertEquals(501L, response.getFirstMessage().getMessageId());
        verify(hashOperations, times(2)).putAll(anyString(), anyMap());
    }

    @Test
    void create_session_algorithm_with_resume_file_success() {
        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest();
        request.setResumeFileId("file123");
        request.setAlgorithmDirection("二叉树、动态规划");
        request.setMode("ALGORITHM");
        request.setStyle("PRESSURE");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(valueOperations.increment(anyString())).thenReturn(2L);
        when(hashOperations.get("interview:resume:file:file123", "extractedText")).thenReturn("简历解析文本");
        when(deepSeekService.generateAlgorithmFlowSteps("二叉树、动态规划", "PRESSURE"))
                .thenReturn(List.of("确认方向", "朴素解法", "复杂度分析", "边界测试", "变式迁移"));
        when(valueOperations.increment("interview:seq:session")).thenReturn(202L);
        when(valueOperations.increment("interview:seq:message")).thenReturn(602L);

        CreateInterviewSessionResponse response = interviewService.createSession(2L, request);

        assertEquals("ALGORITHM", response.getMode());
        assertNotNull(response.getAlgoFlow());
        assertEquals(true, response.getAlgoFlow().getEnabled());
        assertEquals(1, response.getAlgoFlow().getCurrentStep());
        assertEquals("确认方向", response.getAlgoFlow().getSteps().get(0));
    }

    @Test
    void create_session_algorithm_missing_direction() {
        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest();
        request.setResumeFileId("file123");
        request.setMode("ALGORITHM");
        request.setStyle("GUIDED");

        BusinessException ex = assertThrows(BusinessException.class, () -> interviewService.createSession(2L, request));

        assertEquals("参数错误：algorithmDirection 不能为空", ex.getMessage());
    }

    @Test
    void create_session_missing_resume_source() {
        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest();
        request.setJobTitle("Java 后端开发实习生");
        request.setJdText("熟悉 Java 基础和 Spring");
        request.setMode("COMPREHENSIVE");
        request.setStyle("GUIDED");

        BusinessException ex = assertThrows(BusinessException.class, () -> interviewService.createSession(1L, request));

        assertEquals("请上传简历文件", ex.getMessage());
    }

    @Test
    void get_session_info_algorithm_success() {
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "301");
        session.put("userId", "9");
        session.put("sessionCode", "IM-20260321-301");
        session.put("title", "Java 后端开发实习生 - 模拟面试");
        session.put("mode", "ALGORITHM");
        session.put("style", "GUIDED");
        session.put("status", "IN_PROGRESS");
        session.put("welcomeText", "欢迎文案");
        session.put("algoCurrentStep", "3");
        session.put("algoFlowSteps", "方向校准||朴素解法||复杂度||边界Case");
        session.put("startedAt", "2026-03-21T10:30:00");
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("interview:session:301")).thenReturn(session);

        InterviewSessionDetailResponse response = interviewService.getSessionInfo(9L, 301L);

        assertEquals(301L, response.getSessionId());
        assertEquals("ALGORITHM", response.getMode());
        assertNotNull(response.getAlgoFlow());
        assertTrue(response.getAlgoFlow().getEnabled());
        assertEquals(3, response.getAlgoFlow().getCurrentStep());
        assertEquals("方向校准", response.getAlgoFlow().getSteps().get(0));
    }

    @Test
    void get_session_info_forbidden_when_owner_mismatch() {
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "301");
        session.put("userId", "10");
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("interview:session:301")).thenReturn(session);

        BusinessException ex = assertThrows(BusinessException.class, () -> interviewService.getSessionInfo(9L, 301L));

        assertEquals(403, ex.getCode());
    }

    @Test
    void get_session_messages_since_id_success() {
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "301");
        session.put("userId", "9");
        Map<Object, Object> message1002 = new HashMap<>();
        message1002.put("id", "1002");
        message1002.put("role", "assistant");
        message1002.put("kind", "REVIEW");
        message1002.put("turnNo", "1");
        message1002.put("content", "点评内容");
        message1002.put("reviewTitle", "表达能力");
        message1002.put("reviewType", "IMPROVE");
        message1002.put("typing", "false");
        message1002.put("createdAt", "2026-03-21T10:01:00");
        Map<Object, Object> message1003 = new HashMap<>();
        message1003.put("id", "1003");
        message1003.put("role", "assistant");
        message1003.put("kind", "FOLLOWUP");
        message1003.put("turnNo", "2");
        message1003.put("content", "继续追问");
        message1003.put("typing", "true");
        message1003.put("createdAt", "2026-03-21T10:02:00");
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(hashOperations.entries("interview:session:301")).thenReturn(session);
        when(listOperations.range("interview:session:301:messages", 0, -1)).thenReturn(List.of("1001", "1002", "1003"));
        when(hashOperations.entries("interview:session:301:message:1002")).thenReturn(message1002);
        when(hashOperations.entries("interview:session:301:message:1003")).thenReturn(message1003);

        List<InterviewSessionMessageResponse> result = interviewService.getSessionMessages(9L, 301L, 1001L, null);

        assertEquals(2, result.size());
        assertEquals(1002L, result.get(0).getId());
        assertEquals("表达能力", result.get(0).getReviewTitle());
        assertEquals("MARKDOWN", result.get(0).getContentFormat());
        assertEquals(false, result.get(0).getTyping());
        assertEquals(1003L, result.get(1).getId());
        assertEquals(true, result.get(1).getTyping());
    }

    @Test
    void get_session_messages_invalid_limit() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewService.getSessionMessages(9L, 301L, null, 0));

        assertEquals("参数错误：limit 取值范围为 1-100", ex.getMessage());
    }

    @Test
    void submit_answer_success() {
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "301");
        session.put("userId", "9");
        session.put("mode", "ALGORITHM");
        session.put("style", "GUIDED");
        session.put("status", "IN_PROGRESS");
        session.put("currentTurn", "1");
        session.put("algoCurrentStep", "1");
        session.put("algoFlowSteps", "方向校准||朴素解法||复杂度分析");
        SubmitInterviewAnswerRequest request = new SubmitInterviewAnswerRequest();
        request.setContent("我先从朴素解法开始，使用双重循环遍历。");
        request.setTurnNo(1);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries("interview:session:301")).thenReturn(session);
        when(valueOperations.increment("interview:seq:message")).thenReturn(2001L, 2002L);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        SubmitInterviewAnswerResponse response = interviewService.submitAnswer(9L, 301L, request);

        assertNotNull(response.getAssistantMessage());
        assertEquals("ASSISTANT", response.getAssistantMessage().getKind());
        assertEquals("MARKDOWN", response.getAssistantMessage().getContentFormat());
        assertEquals(false, response.getAssistantMessage().getTyping());
        assertNotNull(response.getAssistantMessage().getContent());
        assertEquals(false, response.getIsFinished());
        assertNotNull(response.getAlgoFlow());
        assertEquals(2, response.getAlgoFlow().getCurrentStep());
        verify(deepSeekService, times(1)).generateReviewAndFollowup(any(), any(), any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void submit_answer_turn_mismatch() {
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "301");
        session.put("userId", "9");
        session.put("mode", "COMPREHENSIVE");
        session.put("style", "GUIDED");
        session.put("status", "IN_PROGRESS");
        session.put("currentTurn", "2");
        SubmitInterviewAnswerRequest request = new SubmitInterviewAnswerRequest();
        request.setContent("这是我的回答");
        request.setTurnNo(1);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("interview:session:301")).thenReturn(session);

        BusinessException ex = assertThrows(BusinessException.class, () -> interviewService.submitAnswer(9L, 301L, request));

        assertEquals("轮次不匹配，请刷新会话后重试", ex.getMessage());
    }

    @Test
    void finish_session_success() {
        Map<Object, Object> session = new HashMap<>();
        session.put("id", "301");
        session.put("userId", "9");
        session.put("status", "IN_PROGRESS");
        session.put("currentTurn", "2");
        FinishInterviewRequest request = new FinishInterviewRequest();
        request.setReason("用户主动结束");
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(interviewSessionMapper.upsertFinishedSession(anyLong(), anyLong(), any(), anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn(1);
        when(hashOperations.entries("interview:session:301")).thenReturn(session);
        when(valueOperations.increment("interview:seq:message")).thenReturn(4001L);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        FinishInterviewResponse response = interviewService.finishSession(9L, 301L, request);

        assertEquals(301L, response.getSessionId());
        assertEquals("FINISHED", response.getStatus());
        assertNotNull(response.getFinishedAt());
    }
}
