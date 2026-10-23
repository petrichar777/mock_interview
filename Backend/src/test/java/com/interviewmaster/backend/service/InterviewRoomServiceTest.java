package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.event.RoomReportRequestedEvent;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.InterviewRoomMapper;
import com.interviewmaster.backend.mapper.QuestionMapper;
import com.interviewmaster.backend.mapper.QuestionModuleMapper;
import com.interviewmaster.backend.mapper.RoomQuestionMapper;
import com.interviewmaster.backend.mapper.RoomReportMapper;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.dto.request.CreateRoomRequest;
import com.interviewmaster.backend.model.dto.request.SaveRoomQuestionsRequest;
import com.interviewmaster.backend.model.dto.response.CreateRoomResponse;
import com.interviewmaster.backend.model.dto.response.FinishRoomResponse;
import com.interviewmaster.backend.model.dto.response.RoomDetailResponse;
import com.interviewmaster.backend.model.dto.response.RoomQuestionOptionResponse;
import com.interviewmaster.backend.model.dto.response.RoomReportResponse;
import com.interviewmaster.backend.model.dto.response.RtcTokenResponse;
import com.interviewmaster.backend.model.dto.response.SaveRoomQuestionsResponse;
import com.interviewmaster.backend.model.entity.InterviewRoom;
import com.interviewmaster.backend.model.entity.Question;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.RoomQuestion;
import com.interviewmaster.backend.model.entity.RoomReport;
import com.interviewmaster.backend.model.entity.User;
import com.interviewmaster.backend.model.enums.InterviewRoomStatus;
import com.interviewmaster.backend.model.enums.RoomReportStatus;
import com.interviewmaster.backend.websocket.RoomEventPublisher;
import com.interviewmaster.backend.websocket.RoomEventType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class InterviewRoomServiceTest {

    @Mock
    private InterviewRoomMapper roomMapper;
    @Mock
    private RoomQuestionMapper roomQuestionMapper;
    @Mock
    private RoomReportMapper roomReportMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionModuleMapper questionModuleMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TrtcService trtcService;
    @Mock
    private DeepSeekService deepSeekService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private RoomEventPublisher roomEventPublisher;

    private InterviewRoomService interviewRoomService;

    @BeforeEach
    void setUp() {
        interviewRoomService = new InterviewRoomService(
                roomMapper, roomQuestionMapper, roomReportMapper, questionMapper,
                questionModuleMapper, userMapper, trtcService, deepSeekService,
                new ObjectMapper(), eventPublisher, roomEventPublisher);
    }

    @Test
    void createRoom_snapshotsQuestionsAndReturnsCode() {
        CreateRoomRequest request = createRoomRequest();
        Question q1 = question(101L, 5L, "线程池核心参数");
        Question q2 = question(102L, 5L, "JVM 内存模型");
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(q1, q2));
        QuestionModule module = module(5L, "Java 基础");
        when(questionModuleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(module));
        when(roomMapper.insert(any(InterviewRoom.class))).thenAnswer(invocation -> {
            InterviewRoom room = invocation.getArgument(0);
            room.setId(10L);
            return 1;
        });
        when(roomMapper.updateById(any(InterviewRoom.class))).thenReturn(1);
        when(roomQuestionMapper.batchInsert(anyList())).thenReturn(2);

        CreateRoomResponse response = interviewRoomService.createRoom(1L, request);

        assertEquals(6, response.getRoomCode().length());
        assertEquals(10L, response.getTrtcRoomId().longValue());
        assertEquals(InterviewRoomStatus.WAITING.getCode(), response.getStatus().intValue());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List<RoomQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomQuestionMapper).batchInsert(captor.capture());
        List<RoomQuestion> items = captor.getValue();
        assertEquals(2, items.size());
        assertEquals(101L, items.get(0).getQuestionId().longValue());
        assertEquals("线程池核心参数", items.get(0).getQuestionStem());
        assertEquals("Java 基础", items.get(0).getModuleName());
        assertEquals(1, items.get(0).getOrderNo().intValue());
        assertEquals(0, items.get(0).getResult().intValue());
    }

    @Test
    void listQuestionOptions_returnsOnlineQuestions() {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(101L, 5L, "线程池核心参数")));
        when(questionModuleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                module(5L, "Java 基础")));

        List<RoomQuestionOptionResponse> options = interviewRoomService.listQuestionOptions(null);

        assertEquals(1, options.size());
        assertEquals(101L, options.get(0).getId().longValue());
        assertEquals("Java 基础", options.get(0).getModuleName());
    }

    @Test
    void joinRoom_bindsCandidateAndHidesEvaluationFromCandidate() {
        InterviewRoom waiting = room(InterviewRoomStatus.WAITING, null);
        InterviewRoom joined = room(InterviewRoomStatus.IN_PROGRESS, 2L);
        when(roomMapper.selectOne(any())).thenReturn(waiting, joined);
        when(roomMapper.joinAsCandidate("ABC234", 2L)).thenReturn(1);
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(user(1L, "面试官"), user(2L, "候选人")));
        when(roomQuestionMapper.selectList(any())).thenReturn(List.of(roomQuestion(1L, 1, 3, "答得不错")));

        RoomDetailResponse detail = interviewRoomService.joinRoom(2L, "ABC234");

        assertEquals(InterviewRoomStatus.IN_PROGRESS.getCode(), detail.getStatus().intValue());
        assertEquals(2L, detail.getCandidate().getId().longValue());
        assertNull(detail.getQuestions().get(0).getResult());
        assertNull(detail.getQuestions().get(0).getNote());
        verify(roomEventPublisher).publish(eq("ABC234"), eq(RoomEventType.ROOM_STATUS_CHANGED), any());
    }

    @Test
    void joinRoom_occupiedByAnotherCandidate_throws403() {
        InterviewRoom waiting = room(InterviewRoomStatus.WAITING, null);
        InterviewRoom occupied = room(InterviewRoomStatus.IN_PROGRESS, 3L);
        when(roomMapper.selectOne(any())).thenReturn(waiting, occupied);
        when(roomMapper.joinAsCandidate("ABC234", 2L)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewRoomService.joinRoom(2L, "ABC234"));

        assertEquals(403, ex.getCode());
    }

    @Test
    void joinRoom_interviewer_throwsBusinessException() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.WAITING, null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewRoomService.joinRoom(1L, "ABC234"));

        assertTrue(ex.getMessage().contains("面试官"));
    }

    @Test
    void saveQuestions_asInterviewer_updatesProgress() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.IN_PROGRESS, 2L));
        when(roomQuestionMapper.selectCount(any())).thenReturn(2L);
        when(roomQuestionMapper.batchUpdateEvaluation(eq(10L), anyList())).thenReturn(2);
        when(roomQuestionMapper.selectList(any())).thenReturn(List.of(
                roomQuestion(1L, 1, 3, "ok"),
                roomQuestion(2L, 2, 1, "不会")));

        SaveRoomQuestionsRequest request = saveRequest();
        SaveRoomQuestionsResponse response = interviewRoomService.saveQuestions(1L, "ABC234", request);

        assertEquals(2, response.getUpdatedCount().intValue());
        assertEquals(2, response.getAnsweredCount().intValue());
        assertEquals(2, response.getTotalCount().intValue());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List<RoomQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomQuestionMapper).batchUpdateEvaluation(eq(10L), captor.capture());
        assertEquals("ok", captor.getValue().get(0).getNote());
        verify(roomEventPublisher).publish(eq("ABC234"), eq(RoomEventType.QUESTION_PROGRESS_CHANGED), any());
    }

    @Test
    void saveQuestions_asCandidate_throws403() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.IN_PROGRESS, 2L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewRoomService.saveQuestions(2L, "ABC234", saveRequest()));

        assertEquals(403, ex.getCode());
    }

    @Test
    void saveQuestions_finishedRoom_throws400() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.FINISHED, 2L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewRoomService.saveQuestions(1L, "ABC234", saveRequest()));

        assertEquals(400, ex.getCode());
    }

    @Test
    void issueRtcToken_member_returnsUserSig() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.IN_PROGRESS, 2L));
        when(trtcService.getSdkAppId()).thenReturn(1400000000L);
        when(trtcService.issueUserSig(2L)).thenReturn("sig");
        when(trtcService.getUserSigExpireSeconds()).thenReturn(7200L);

        RtcTokenResponse response = interviewRoomService.issueRtcToken(2L, "ABC234");

        assertEquals(1400000000L, response.getSdkAppId().longValue());
        assertEquals("u2", response.getUserId());
        assertEquals("sig", response.getUserSig());
        assertEquals(7200L, response.getExpireSeconds().longValue());
        assertEquals(10L, response.getRoomId().longValue());
    }

    @Test
    void issueRtcToken_nonMember_throws403() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.IN_PROGRESS, 2L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewRoomService.issueRtcToken(3L, "ABC234"));

        assertEquals(403, ex.getCode());
    }

    @Test
    void finishRoom_interviewer_createsReportAndPublishesEvent() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.IN_PROGRESS, 2L));
        when(roomMapper.finishRoom(10L)).thenReturn(1);
        when(roomReportMapper.selectOne(any())).thenReturn(null);
        when(roomReportMapper.insert(any(RoomReport.class))).thenAnswer(invocation -> {
            RoomReport report = invocation.getArgument(0);
            report.setId(20L);
            return 1;
        });

        FinishRoomResponse response = interviewRoomService.finishRoom(1L, "ABC234");

        assertEquals(InterviewRoomStatus.FINISHED.getCode(), response.getRoomStatus().intValue());
        assertEquals(RoomReportStatus.GENERATING.getCode(), response.getReportStatus().intValue());
        verify(eventPublisher).publishEvent(any(RoomReportRequestedEvent.class));
        verify(roomEventPublisher).publish(eq("ABC234"), eq(RoomEventType.ROOM_STATUS_CHANGED), any());
    }

    @Test
    void finishRoom_alreadyFinishedFailedReport_resetsAndRepublishes() {
        InterviewRoom room = room(InterviewRoomStatus.FINISHED, 2L);
        RoomReport failed = report(20L, RoomReportStatus.FAILED);
        when(roomMapper.selectOne(any())).thenReturn(room);
        when(roomReportMapper.selectOne(any())).thenReturn(failed);
        when(roomReportMapper.resetForRetry(20L)).thenReturn(1);

        FinishRoomResponse response = interviewRoomService.finishRoom(1L, "ABC234");

        assertEquals(RoomReportStatus.GENERATING.getCode(), response.getReportStatus().intValue());
        verify(roomReportMapper).resetForRetry(20L);
        verify(eventPublisher).publishEvent(any(RoomReportRequestedEvent.class));
        verify(roomMapper, never()).finishRoom(anyLong());
    }

    @Test
    void finishRoom_beforeCandidateJoined_throws400() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.IN_PROGRESS, null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewRoomService.finishRoom(1L, "ABC234"));

        assertEquals(400, ex.getCode());
        verify(roomMapper, never()).finishRoom(anyLong());
    }

    @Test
    void generateReport_aiSuccess_updatesReportAndPublishesReady() throws Exception {
        InterviewRoom room = room(InterviewRoomStatus.FINISHED, 2L);
        RoomReport report = report(20L, RoomReportStatus.GENERATING);
        when(roomReportMapper.selectOne(any())).thenReturn(report);
        when(roomMapper.selectById(10L)).thenReturn(room);
        when(roomQuestionMapper.selectList(any())).thenReturn(List.of(
                roomQuestion(1L, 1, 3, "答得好"),
                roomQuestion(2L, 2, 1, "线程池不熟"),
                roomQuestion(3L, 3, 0, null)));
        when(deepSeekService.generateRoomSummary(anyString(), anyString())).thenReturn(
                "{\"overallScore\":85,\"summary\":\"## 总结\","
                        + "\"moduleMastery\":[{\"moduleId\":5,\"moduleName\":\"Java 基础\","
                        + "\"level\":\"弱\",\"comment\":\"线程池需要加强\"}],"
                        + "\"strengths\":[\"表达清晰\"],\"weaknesses\":[\"并发基础薄弱\"],"
                        + "\"suggestions\":[\"复习线程池\"]}");
        when(deepSeekService.getModelName()).thenReturn("deepseek-v4-flash");
        Question recommendation = question(201L, 5L, "线程池进阶");
        when(questionMapper.selectList(any())).thenReturn(List.of(recommendation));
        when(roomReportMapper.markSuccess(eq(20L), eq(85), anyString(), anyString(),
                eq("deepseek-v4-flash"))).thenReturn(1);

        interviewRoomService.generateReport(10L);

        verify(deepSeekService).generateRoomSummary(anyString(), anyString());
        verify(roomReportMapper).markSuccess(eq(20L), eq(85), anyString(), anyString(),
                eq("deepseek-v4-flash"));
        verify(roomEventPublisher).publish(eq("ABC234"), eq(RoomEventType.REPORT_READY), any());
    }

    @Test
    void generateReport_aiFailure_marksReportFailed() {
        InterviewRoom room = room(InterviewRoomStatus.FINISHED, 2L);
        RoomReport report = report(20L, RoomReportStatus.GENERATING);
        when(roomReportMapper.selectOne(any())).thenReturn(report);
        when(roomMapper.selectById(10L)).thenReturn(room);
        when(roomQuestionMapper.selectList(any())).thenReturn(List.of(roomQuestion(1L, 1, 3, "ok")));
        when(deepSeekService.generateRoomSummary(anyString(), anyString()))
                .thenThrow(new BusinessException(500, "AI 请求失败"));
        when(roomReportMapper.markFailed(eq(20L), anyString())).thenReturn(1);

        interviewRoomService.generateReport(10L);

        verify(roomReportMapper).markFailed(eq(20L), anyString());
        verify(roomEventPublisher).publish(eq("ABC234"), eq(RoomEventType.REPORT_READY), any());
    }

    @Test
    void getReport_success_returnsPayload() {
        InterviewRoom room = room(InterviewRoomStatus.FINISHED, 2L);
        RoomReport report = report(20L, RoomReportStatus.SUCCESS);
        report.setOverallScore(88);
        report.setSummary("整体不错");
        report.setReportJson("{\"overallScore\":88,\"summary\":\"整体不错\","
                + "\"strengths\":[\"表达清晰\"],\"recommendedQuestions\":[]}");
        when(roomMapper.selectOne(any())).thenReturn(room);
        when(roomReportMapper.selectOne(any())).thenReturn(report);

        RoomReportResponse response = interviewRoomService.getReport(2L, "ABC234");

        assertEquals(88, response.getOverallScore().intValue());
        assertEquals("整体不错", response.getSummary());
        assertNotNull(response.getReport());
        assertEquals(88, response.getReport().getOverallScore().intValue());
        assertEquals(1, response.getReport().getStrengths().size());
    }

    @Test
    void getReport_beforeFinish_throws400() {
        when(roomMapper.selectOne(any())).thenReturn(room(InterviewRoomStatus.IN_PROGRESS, 2L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewRoomService.getReport(2L, "ABC234"));

        assertEquals(400, ex.getCode());
    }

    private CreateRoomRequest createRoomRequest() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.setPosition("Java 后端");
        request.setLevel("初级");
        request.setQuestionIds(List.of(101L, 102L));
        return request;
    }

    private SaveRoomQuestionsRequest saveRequest() {
        SaveRoomQuestionsRequest.RoomQuestionItem first =
                new SaveRoomQuestionsRequest.RoomQuestionItem();
        first.setRoomQuestionId(1L);
        first.setResult(3);
        first.setNote("ok");
        SaveRoomQuestionsRequest.RoomQuestionItem second =
                new SaveRoomQuestionsRequest.RoomQuestionItem();
        second.setRoomQuestionId(2L);
        second.setResult(1);
        second.setNote("不会");
        SaveRoomQuestionsRequest request = new SaveRoomQuestionsRequest();
        request.setQuestions(List.of(first, second));
        return request;
    }

    private InterviewRoom room(InterviewRoomStatus status, Long candidateId) {
        InterviewRoom room = new InterviewRoom();
        room.setId(10L);
        room.setRoomCode("ABC234");
        room.setTrtcRoomId(10L);
        room.setInterviewerId(1L);
        room.setCandidateId(candidateId);
        room.setPosition("Java 后端");
        room.setLevel("初级");
        room.setStatus(status.getCode());
        room.setCreatedAt(LocalDateTime.of(2026, 10, 23, 10, 0));
        return room;
    }

    private Question question(long id, long moduleId, String stem) {
        Question question = new Question();
        question.setId(id);
        question.setModuleId(moduleId);
        question.setStem(stem);
        question.setStatus(0);
        return question;
    }

    private QuestionModule module(long id, String name) {
        QuestionModule module = new QuestionModule();
        module.setId(id);
        module.setName(name);
        return module;
    }

    private RoomQuestion roomQuestion(long id, int orderNo, int result, String note) {
        RoomQuestion question = new RoomQuestion();
        question.setId(id);
        question.setRoomId(10L);
        question.setQuestionId(100L + id);
        question.setQuestionStem("题目" + id);
        question.setModuleId(5L);
        question.setModuleName("Java 基础");
        question.setOrderNo(orderNo);
        question.setResult(result);
        question.setNote(note);
        return question;
    }

    private RoomReport report(long id, RoomReportStatus status) {
        RoomReport report = new RoomReport();
        report.setId(id);
        report.setRoomId(10L);
        report.setStatus(status.getCode());
        return report;
    }

    private User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
