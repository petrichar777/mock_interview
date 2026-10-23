package com.interviewmaster.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.interviewmaster.backend.model.dto.response.RoomParticipantResponse;
import com.interviewmaster.backend.model.dto.response.RoomQuestionOptionResponse;
import com.interviewmaster.backend.model.dto.response.RoomQuestionResponse;
import com.interviewmaster.backend.model.dto.response.RoomReportPayload;
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
import com.interviewmaster.backend.model.enums.RoomQuestionResult;
import com.interviewmaster.backend.model.enums.RoomReportStatus;
import com.interviewmaster.backend.util.RoomCodeGenerator;
import com.interviewmaster.backend.websocket.RoomEventPublisher;
import com.interviewmaster.backend.websocket.RoomEventType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewRoomService {

    private static final int MAX_QUESTIONS = 20;
    private static final int ROOM_CODE_RETRY = 5;
    private static final int RECOMMEND_LIMIT = 5;
    private static final int QUESTION_STATUS_ONLINE = 0;
    private static final int MAX_SCORE = 100;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String DEFAULT_MODULE_NAME = "未分类";
    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String ROLE_CANDIDATE = "CANDIDATE";

    private final InterviewRoomMapper roomMapper;
    private final RoomQuestionMapper roomQuestionMapper;
    private final RoomReportMapper roomReportMapper;
    private final QuestionMapper questionMapper;
    private final QuestionModuleMapper questionModuleMapper;
    private final UserMapper userMapper;
    private final TrtcService trtcService;
    private final DeepSeekService deepSeekService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RoomEventPublisher roomEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public CreateRoomResponse createRoom(Long interviewerId, CreateRoomRequest request) {
        List<Long> questionIds = request.getQuestionIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            throw new BusinessException("至少选择一道题");
        }
        if (questionIds.size() > MAX_QUESTIONS) {
            throw new BusinessException("最多选择" + MAX_QUESTIONS + "道题");
        }

        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        for (Long questionId : questionIds) {
            Question question = questionMap.get(questionId);
            if (question == null) {
                throw new BusinessException("部分题目不存在，请刷新题库后重试");
            }
            if (!Integer.valueOf(QUESTION_STATUS_ONLINE).equals(question.getStatus())) {
                throw new BusinessException("题目《" + shortStem(question.getStem()) + "》已下架");
            }
        }
        Map<Long, String> moduleNames = loadModuleNames(questionMap.values());

        InterviewRoom room = new InterviewRoom();
        room.setInterviewerId(interviewerId);
        room.setPosition(request.getPosition().trim());
        room.setLevel(request.getLevel().trim());
        room.setStatus(InterviewRoomStatus.WAITING.getCode());
        LocalDateTime now = LocalDateTime.now();
        room.setCreatedAt(now);
        room.setUpdatedAt(now);
        insertRoomWithCodeRetry(room);

        room.setTrtcRoomId(room.getId());
        room.setUpdatedAt(now);
        roomMapper.updateById(room);

        List<RoomQuestion> roomQuestions = new ArrayList<>();
        int orderNo = 1;
        for (Long questionId : questionIds) {
            Question question = questionMap.get(questionId);
            RoomQuestion item = new RoomQuestion();
            item.setRoomId(room.getId());
            item.setQuestionId(question.getId());
            item.setQuestionStem(question.getStem());
            item.setModuleId(question.getModuleId());
            item.setModuleName(moduleNames.getOrDefault(question.getModuleId(), DEFAULT_MODULE_NAME));
            item.setOrderNo(orderNo++);
            item.setResult(RoomQuestionResult.UNRATED.getCode());
            roomQuestions.add(item);
        }
        roomQuestionMapper.batchInsert(roomQuestions);

        CreateRoomResponse response = new CreateRoomResponse();
        response.setRoomCode(room.getRoomCode());
        response.setTrtcRoomId(room.getTrtcRoomId());
        response.setStatus(room.getStatus());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public RoomDetailResponse joinRoom(Long userId, String roomCode) {
        InterviewRoom room = findRoom(roomCode);
        if (userId.equals(room.getInterviewerId())) {
            throw new BusinessException("面试官无需加入房间");
        }
        if (InterviewRoomStatus.isFinished(room.getStatus())) {
            throw new BusinessException("面试已结束，无法加入");
        }

        int updated = roomMapper.joinAsCandidate(roomCode, userId);
        if (updated == 0) {
            InterviewRoom latest = findRoom(roomCode);
            if (InterviewRoomStatus.isFinished(latest.getStatus())) {
                throw new BusinessException("面试已结束，无法加入");
            }
            if (userId.equals(latest.getInterviewerId())) {
                throw new BusinessException("面试官无需加入房间");
            }
            if (latest.getCandidateId() != null && !latest.getCandidateId().equals(userId)) {
                throw new BusinessException(403, "房间已绑定其他候选人");
            }
        }

        InterviewRoom latest = findRoom(roomCode);
        roomEventPublisher.publish(roomCode, RoomEventType.ROOM_STATUS_CHANGED,
                Map.of("status", latest.getStatus(), "candidateId", userId));
        return buildRoomDetail(latest, userId);
    }

    public RoomDetailResponse getRoomDetail(Long userId, String roomCode) {
        InterviewRoom room = findRoom(roomCode);
        requireMember(room, userId);
        return buildRoomDetail(room, userId);
    }

    public List<RoomQuestionOptionResponse> listQuestionOptions(Long moduleId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, QUESTION_STATUS_ONLINE)
                .orderByAsc(Question::getModuleId)
                .orderByAsc(Question::getDifficulty)
                .orderByAsc(Question::getId)
                .last("LIMIT 100");
        if (moduleId != null) {
            wrapper.eq(Question::getModuleId, moduleId);
        }
        List<Question> questions = questionMapper.selectList(wrapper);
        Map<Long, String> moduleNames = loadModuleNames(questions);
        List<RoomQuestionOptionResponse> options = new ArrayList<>();
        for (Question question : questions) {
            RoomQuestionOptionResponse option = new RoomQuestionOptionResponse();
            option.setId(question.getId());
            option.setStem(question.getStem());
            option.setModuleId(question.getModuleId());
            option.setModuleName(moduleNames.getOrDefault(question.getModuleId(), DEFAULT_MODULE_NAME));
            option.setDifficulty(question.getDifficulty());
            options.add(option);
        }
        return options;
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveRoomQuestionsResponse saveQuestions(Long userId, String roomCode,
                                                   SaveRoomQuestionsRequest request) {
        InterviewRoom room = findRoom(roomCode);
        requireInterviewer(room, userId);
        if (InterviewRoomStatus.isFinished(room.getStatus())) {
            throw new BusinessException("面试已结束，不能再修改面试单");
        }

        Set<Long> ids = new LinkedHashSet<>();
        for (SaveRoomQuestionsRequest.RoomQuestionItem item : request.getQuestions()) {
            if (!RoomQuestionResult.isValid(item.getResult())) {
                throw new BusinessException("评价结果不合法");
            }
            if (!ids.add(item.getRoomQuestionId())) {
                throw new BusinessException("面试单题目重复");
            }
        }
        Long existing = roomQuestionMapper.selectCount(new LambdaQueryWrapper<RoomQuestion>()
                .eq(RoomQuestion::getRoomId, room.getId())
                .in(RoomQuestion::getId, ids));
        if (existing == null || existing != ids.size()) {
            throw new BusinessException("面试单题目不存在");
        }

        List<RoomQuestion> updates = new ArrayList<>();
        for (SaveRoomQuestionsRequest.RoomQuestionItem item : request.getQuestions()) {
            RoomQuestion update = new RoomQuestion();
            update.setId(item.getRoomQuestionId());
            update.setResult(item.getResult());
            update.setNote(trimToNull(item.getNote()));
            updates.add(update);
        }
        int updated = roomQuestionMapper.batchUpdateEvaluation(room.getId(), updates);

        List<RoomQuestion> questions = listRoomQuestions(room.getId());
        int answered = countAnswered(questions);
        roomEventPublisher.publish(roomCode, RoomEventType.QUESTION_PROGRESS_CHANGED,
                Map.of("answered", answered, "total", questions.size()));

        SaveRoomQuestionsResponse response = new SaveRoomQuestionsResponse();
        response.setUpdatedCount(updated);
        response.setAnsweredCount(answered);
        response.setTotalCount(questions.size());
        return response;
    }

    public RtcTokenResponse issueRtcToken(Long userId, String roomCode) {
        InterviewRoom room = findRoom(roomCode);
        requireMember(room, userId);
        if (InterviewRoomStatus.isFinished(room.getStatus())) {
            throw new BusinessException("面试已结束，无法获取音视频凭证");
        }
        if (room.getTrtcRoomId() == null) {
            throw new BusinessException(500, "房间音视频编号缺失");
        }

        RtcTokenResponse response = new RtcTokenResponse();
        response.setSdkAppId(trtcService.getSdkAppId());
        response.setUserId("u" + userId);
        response.setUserSig(trtcService.issueUserSig(userId));
        response.setExpireSeconds(trtcService.getUserSigExpireSeconds());
        response.setRoomId(room.getTrtcRoomId());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public FinishRoomResponse finishRoom(Long userId, String roomCode) {
        InterviewRoom room = findRoom(roomCode);
        requireInterviewer(room, userId);

        if (InterviewRoomStatus.isFinished(room.getStatus())) {
            RoomReport existing = findReport(room.getId());
            if (existing == null) {
                existing = createGeneratingReport(room.getId());
                eventPublisher.publishEvent(new RoomReportRequestedEvent(room.getId()));
            } else if (Integer.valueOf(RoomReportStatus.FAILED.getCode()).equals(existing.getStatus())) {
                resetReportForRetry(existing.getId());
                existing.setStatus(RoomReportStatus.GENERATING.getCode());
                eventPublisher.publishEvent(new RoomReportRequestedEvent(room.getId()));
            }
            return buildFinishResponse(room, existing.getStatus());
        }
        if (room.getCandidateId() == null) {
            throw new BusinessException("候选人尚未加入房间");
        }

        int updated = roomMapper.finishRoom(room.getId());
        if (updated == 0) {
            InterviewRoom latest = findRoom(roomCode);
            RoomReport existing = findReport(latest.getId());
            if (existing == null) {
                existing = createGeneratingReport(latest.getId());
                eventPublisher.publishEvent(new RoomReportRequestedEvent(latest.getId()));
            }
            return buildFinishResponse(latest, existing.getStatus());
        }

        RoomReport report = findReport(room.getId());
        if (report == null) {
            report = createGeneratingReport(room.getId());
        }
        eventPublisher.publishEvent(new RoomReportRequestedEvent(room.getId()));
        roomEventPublisher.publish(roomCode, RoomEventType.ROOM_STATUS_CHANGED,
                Map.of("status", InterviewRoomStatus.FINISHED.getCode()));
        return buildFinishResponse(room, report.getStatus());
    }

    public RoomReportResponse getReport(Long userId, String roomCode) {
        InterviewRoom room = findRoom(roomCode);
        requireMember(room, userId);
        if (!InterviewRoomStatus.isFinished(room.getStatus())) {
            throw new BusinessException("面试尚未结束");
        }
        RoomReport report = findReport(room.getId());
        if (report == null) {
            throw new BusinessException(404, "面试报告不存在");
        }

        RoomReportResponse response = new RoomReportResponse();
        response.setRoomCode(room.getRoomCode());
        response.setStatus(report.getStatus());
        response.setOverallScore(report.getOverallScore());
        response.setSummary(report.getSummary());
        response.setErrorMessage(report.getErrorMessage());
        response.setCreatedAt(report.getCreatedAt());
        if (Integer.valueOf(RoomReportStatus.SUCCESS.getCode()).equals(report.getStatus())
                && StringUtils.hasText(report.getReportJson())) {
            try {
                response.setReport(objectMapper.readValue(
                        report.getReportJson(), RoomReportPayload.class));
            } catch (Exception ex) {
                log.warn("failed to parse room report json, roomId={}", room.getId(), ex);
            }
        }
        return response;
    }

    public void generateReport(Long roomId) {
        RoomReport report = findReport(roomId);
        if (report == null) {
            log.warn("room report record not found, roomId={}", roomId);
            return;
        }
        if (Integer.valueOf(RoomReportStatus.SUCCESS.getCode()).equals(report.getStatus())) {
            return;
        }
        InterviewRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            return;
        }
        try {
            List<RoomQuestion> questions = listRoomQuestions(roomId);
            String content = deepSeekService.generateRoomSummary(
                    buildReportSystemPrompt(), buildReportUserPrompt(room, questions));
            RoomReportPayload payload = parseReportPayload(content);
            normalizePayload(payload, questions);
            String reportJson = objectMapper.writeValueAsString(payload);

            roomReportMapper.markSuccess(report.getId(), payload.getOverallScore(),
                    payload.getSummary(), reportJson, deepSeekService.getModelName());

            roomEventPublisher.publish(room.getRoomCode(), RoomEventType.REPORT_READY,
                    Map.of("status", RoomReportStatus.SUCCESS.getCode(),
                            "overallScore", payload.getOverallScore()));
        } catch (Exception ex) {
            log.error("failed to generate room report, roomId={}", roomId, ex);
            roomReportMapper.markFailed(report.getId(),
                    truncate(ex.getMessage(), MAX_ERROR_LENGTH));
            roomEventPublisher.publish(room.getRoomCode(), RoomEventType.REPORT_READY,
                    Map.of("status", RoomReportStatus.FAILED.getCode()));
        }
    }

    private void insertRoomWithCodeRetry(InterviewRoom room) {
        for (int attempt = 0; attempt < ROOM_CODE_RETRY; attempt++) {
            room.setRoomCode(RoomCodeGenerator.generate());
            try {
                roomMapper.insert(room);
                return;
            } catch (DuplicateKeyException ex) {
                log.warn("room code collision, retrying, attempt={}", attempt + 1);
                room.setId(null);
            }
        }
        throw new BusinessException(500, "房间创建失败，请重试");
    }

    private InterviewRoom findRoom(String roomCode) {
        if (!StringUtils.hasText(roomCode)) {
            throw new BusinessException(404, "房间不存在");
        }
        InterviewRoom room = roomMapper.selectOne(new LambdaQueryWrapper<InterviewRoom>()
                .eq(InterviewRoom::getRoomCode, roomCode.trim()));
        if (room == null) {
            throw new BusinessException(404, "房间不存在");
        }
        return room;
    }

    private void requireMember(InterviewRoom room, Long userId) {
        if (!userId.equals(room.getInterviewerId()) && !userId.equals(room.getCandidateId())) {
            throw new BusinessException(403, "无权访问该房间");
        }
    }

    private void requireInterviewer(InterviewRoom room, Long userId) {
        if (!userId.equals(room.getInterviewerId())) {
            throw new BusinessException(403, "仅面试官可执行该操作");
        }
    }

    private RoomDetailResponse buildRoomDetail(InterviewRoom room, Long userId) {
        boolean interviewerView = userId.equals(room.getInterviewerId());
        List<RoomQuestion> questions = listRoomQuestions(room.getId());
        Map<Long, User> users = loadUsers(room);

        RoomDetailResponse response = new RoomDetailResponse();
        response.setRoomCode(room.getRoomCode());
        response.setStatus(room.getStatus());
        response.setPosition(room.getPosition());
        response.setLevel(room.getLevel());
        response.setTrtcRoomId(room.getTrtcRoomId());
        response.setInterviewer(buildParticipant(room.getInterviewerId(), ROLE_INTERVIEWER, users));
        response.setCandidate(buildParticipant(room.getCandidateId(), ROLE_CANDIDATE, users));
        response.setCreatedAt(room.getCreatedAt());
        response.setStartedAt(room.getStartedAt());
        response.setFinishedAt(room.getFinishedAt());

        List<RoomQuestionResponse> questionResponses = new ArrayList<>();
        for (RoomQuestion question : questions) {
            RoomQuestionResponse item = new RoomQuestionResponse();
            item.setId(question.getId());
            item.setQuestionId(question.getQuestionId());
            item.setQuestionStem(question.getQuestionStem());
            item.setModuleId(question.getModuleId());
            item.setModuleName(question.getModuleName());
            item.setOrderNo(question.getOrderNo());
            if (interviewerView) {
                item.setResult(question.getResult());
                item.setNote(question.getNote());
            }
            questionResponses.add(item);
        }
        response.setQuestions(questionResponses);
        response.setAnsweredCount(countAnswered(questions));
        response.setTotalCount(questions.size());
        return response;
    }

    private Map<Long, User> loadUsers(InterviewRoom room) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(room.getInterviewerId());
        if (room.getCandidateId() != null) {
            ids.add(room.getCandidateId());
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private RoomParticipantResponse buildParticipant(Long id, String role, Map<Long, User> users) {
        if (id == null) {
            return null;
        }
        RoomParticipantResponse participant = new RoomParticipantResponse();
        participant.setId(id);
        participant.setRole(role);
        User user = users.get(id);
        participant.setUsername(user == null || !StringUtils.hasText(user.getUsername())
                ? "用户" + id : user.getUsername());
        return participant;
    }

    private List<RoomQuestion> listRoomQuestions(Long roomId) {
        return roomQuestionMapper.selectList(new LambdaQueryWrapper<RoomQuestion>()
                .eq(RoomQuestion::getRoomId, roomId)
                .orderByAsc(RoomQuestion::getOrderNo));
    }

    private int countAnswered(List<RoomQuestion> questions) {
        return (int) questions.stream()
                .filter(question -> question.getResult() != null
                        && question.getResult() > RoomQuestionResult.UNRATED.getCode())
                .count();
    }

    private Map<Long, String> loadModuleNames(Iterable<Question> questions) {
        Set<Long> moduleIds = new LinkedHashSet<>();
        for (Question question : questions) {
            if (question.getModuleId() != null) {
                moduleIds.add(question.getModuleId());
            }
        }
        if (moduleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return questionModuleMapper.selectBatchIds(moduleIds).stream()
                .collect(Collectors.toMap(QuestionModule::getId,
                        module -> module.getName() == null ? DEFAULT_MODULE_NAME : module.getName()));
    }

    private RoomReport createGeneratingReport(Long roomId) {
        RoomReport report = new RoomReport();
        report.setRoomId(roomId);
        report.setStatus(RoomReportStatus.GENERATING.getCode());
        try {
            roomReportMapper.insert(report);
        } catch (DuplicateKeyException ex) {
            RoomReport existing = findReport(roomId);
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
        return report;
    }

    private void resetReportForRetry(Long reportId) {
        roomReportMapper.resetForRetry(reportId);
    }

    private RoomReport findReport(Long roomId) {
        return roomReportMapper.selectOne(new LambdaQueryWrapper<RoomReport>()
                .eq(RoomReport::getRoomId, roomId));
    }

    private FinishRoomResponse buildFinishResponse(InterviewRoom room, Integer reportStatus) {
        FinishRoomResponse response = new FinishRoomResponse();
        response.setRoomCode(room.getRoomCode());
        response.setRoomStatus(InterviewRoomStatus.FINISHED.getCode());
        response.setReportStatus(reportStatus);
        return response;
    }

    private String buildReportSystemPrompt() {
        return "你是资深技术面试官，刚刚完成了一场双人技术面试。"
                + "请严格根据面试官在结构化面试单上留下的逐题评价和备注，对候选人进行总结。"
                + "每条结论必须能对应到具体题目或备注，不要空泛评价。"
                + "result=未评的题目不计入评分，但要在总结中说明未覆盖。"
                + "你必须只输出 JSON，不要 Markdown 代码块，不要任何额外文本。"
                + "JSON 格式："
                + "{\"overallScore\":0,\"summary\":\"Markdown格式的面试总结\","
                + "\"moduleMastery\":[{\"moduleId\":1,\"moduleName\":\"模块名\",\"level\":\"强/中/弱\",\"comment\":\"点评\"}],"
                + "\"strengths\":[\"亮点1\",\"亮点2\",\"亮点3\"],"
                + "\"weaknesses\":[\"不足1\",\"不足2\",\"不足3\"],"
                + "\"suggestions\":[\"建议1\",\"建议2\"]}。"
                + "overallScore 为 0-100 的整数；moduleMastery 只覆盖面试单中出现的模块；"
                + "summary 用 Markdown，包含综合表现、亮点、不足和改进建议。";
    }

    private String buildReportUserPrompt(InterviewRoom room, List<RoomQuestion> questions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("岗位：").append(safe(room.getPosition()))
                .append("\n级别：").append(safe(room.getLevel()))
                .append("\n\n模块列表（moduleId:名称）：\n");
        Map<Long, String> modules = new LinkedHashMap<>();
        for (RoomQuestion question : questions) {
            if (question.getModuleId() != null) {
                modules.putIfAbsent(question.getModuleId(),
                        question.getModuleName() == null ? DEFAULT_MODULE_NAME : question.getModuleName());
            }
        }
        if (modules.isEmpty()) {
            prompt.append("无\n");
        } else {
            modules.forEach((id, name) -> prompt.append(id).append(":").append(name).append('\n'));
        }

        prompt.append("\n结构化面试单：\n");
        for (RoomQuestion question : questions) {
            prompt.append("第").append(question.getOrderNo()).append("题")
                    .append("｜模块：").append(question.getModuleName() == null
                            ? DEFAULT_MODULE_NAME : question.getModuleName())
                    .append("｜评价：").append(RoomQuestionResult.labelOf(question.getResult()))
                    .append('\n').append("题干：").append(safe(question.getQuestionStem()))
                    .append('\n');
            if (StringUtils.hasText(question.getNote())) {
                prompt.append("面试官备注：").append(question.getNote().trim()).append('\n');
            }
            prompt.append('\n');
        }
        prompt.append("请严格按照指定 JSON 格式输出总结。");
        return prompt.toString();
    }

    private RoomReportPayload parseReportPayload(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            int firstNewline = normalized.indexOf('\n');
            if (firstNewline > 0) {
                normalized = normalized.substring(firstNewline + 1).trim();
            }
            if (normalized.endsWith("```")) {
                normalized = normalized.substring(0, normalized.length() - 3).trim();
            }
        }
        try {
            return objectMapper.readValue(normalized, RoomReportPayload.class);
        } catch (Exception ignored) {
        }
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readValue(
                        normalized.substring(start, end + 1), RoomReportPayload.class);
            } catch (Exception ignored) {
            }
        }
        throw new BusinessException(500, "AI 报告解析失败");
    }

    private void normalizePayload(RoomReportPayload payload, List<RoomQuestion> questions) {
        payload.setModuleMastery(payload.getModuleMastery() == null
                ? new ArrayList<>() : payload.getModuleMastery());
        payload.setStrengths(normalizeList(payload.getStrengths()));
        payload.setWeaknesses(normalizeList(payload.getWeaknesses()));
        payload.setSuggestions(normalizeList(payload.getSuggestions()));

        Map<Long, String> moduleNames = new LinkedHashMap<>();
        Map<String, Long> moduleIds = new LinkedHashMap<>();
        for (RoomQuestion question : questions) {
            if (question.getModuleId() != null) {
                String name = question.getModuleName() == null
                        ? DEFAULT_MODULE_NAME : question.getModuleName();
                moduleNames.putIfAbsent(question.getModuleId(), name);
                moduleIds.putIfAbsent(name, question.getModuleId());
            }
        }
        for (RoomReportPayload.ModuleMastery mastery : payload.getModuleMastery()) {
            if (mastery.getModuleId() == null && mastery.getModuleName() != null) {
                mastery.setModuleId(moduleIds.get(mastery.getModuleName().trim()));
            }
            if (!StringUtils.hasText(mastery.getModuleName()) && mastery.getModuleId() != null) {
                mastery.setModuleName(moduleNames.get(mastery.getModuleId()));
            }
        }

        int score = payload.getOverallScore() == null
                ? computeScore(questions) : payload.getOverallScore();
        payload.setOverallScore(Math.max(0, Math.min(MAX_SCORE, score)));

        payload.setUncoveredQuestions(questions.stream()
                .filter(question -> question.getResult() == null
                        || question.getResult() == RoomQuestionResult.UNRATED.getCode())
                .map(RoomQuestion::getQuestionStem)
                .toList());
        payload.setRecommendedQuestions(recommendQuestions(payload, questions, moduleNames));
        if (!StringUtils.hasText(payload.getSummary())) {
            payload.setSummary(buildMarkdownSummary(payload));
        }
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private int computeScore(List<RoomQuestion> questions) {
        int rated = 0;
        int total = 0;
        for (RoomQuestion question : questions) {
            if (question.getResult() != null
                    && question.getResult() > RoomQuestionResult.UNRATED.getCode()) {
                rated++;
                total += question.getResult();
            }
        }
        if (rated == 0) {
            return 0;
        }
        return (int) Math.round(total * 100.0 / (rated * 3.0));
    }

    private List<RoomReportPayload.RecommendedQuestion> recommendQuestions(
            RoomReportPayload payload, List<RoomQuestion> questions, Map<Long, String> moduleNames) {
        Set<Long> weakModuleIds = payload.getModuleMastery().stream()
                .filter(mastery -> mastery.getModuleId() != null
                        && mastery.getLevel() != null && mastery.getLevel().contains("弱"))
                .map(RoomReportPayload.ModuleMastery::getModuleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (weakModuleIds.isEmpty()) {
            weakModuleIds = payload.getModuleMastery().stream()
                    .filter(mastery -> mastery.getModuleId() != null
                            && mastery.getLevel() != null && mastery.getLevel().contains("中"))
                    .map(RoomReportPayload.ModuleMastery::getModuleId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (weakModuleIds.isEmpty()) {
            weakModuleIds.addAll(moduleNames.keySet());
        }
        if (weakModuleIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> usedQuestionIds = questions.stream()
                .map(RoomQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .in(Question::getModuleId, weakModuleIds)
                .eq(Question::getStatus, QUESTION_STATUS_ONLINE);
        if (!usedQuestionIds.isEmpty()) {
            wrapper.notIn(Question::getId, usedQuestionIds);
        }
        wrapper.orderByAsc(Question::getDifficulty).last("LIMIT " + RECOMMEND_LIMIT);

        List<RoomReportPayload.RecommendedQuestion> result = new ArrayList<>();
        for (Question question : questionMapper.selectList(wrapper)) {
            RoomReportPayload.RecommendedQuestion recommended =
                    new RoomReportPayload.RecommendedQuestion();
            recommended.setQuestionId(question.getId());
            recommended.setQuestionStem(question.getStem());
            recommended.setModuleName(moduleNames.get(question.getModuleId()));
            result.add(recommended);
        }
        return result;
    }

    private String buildMarkdownSummary(RoomReportPayload payload) {
        StringBuilder summary = new StringBuilder("## 面试总结\n\n");
        summary.append("**综合评分：** ").append(payload.getOverallScore()).append("/100\n\n");
        appendList(summary, "亮点", payload.getStrengths());
        appendList(summary, "不足", payload.getWeaknesses());
        appendList(summary, "改进建议", payload.getSuggestions());
        appendList(summary, "未覆盖题目", payload.getUncoveredQuestions());
        return summary.toString();
    }

    private void appendList(StringBuilder summary, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        summary.append("### ").append(title).append('\n');
        for (String item : items) {
            summary.append("- ").append(item).append('\n');
        }
        summary.append('\n');
    }

    private String shortStem(String stem) {
        if (!StringUtils.hasText(stem)) {
            return "未命名题目";
        }
        String trimmed = stem.trim();
        return trimmed.length() <= 20 ? trimmed : trimmed.substring(0, 20) + "...";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未填写";
    }
}
