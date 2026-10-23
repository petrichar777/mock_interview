package com.interviewmaster.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.InterviewMessageMapper;
import com.interviewmaster.backend.model.dto.response.EvaluateInterviewResponse;
import com.interviewmaster.backend.model.dto.response.InterviewReportListResponse;
import com.interviewmaster.backend.model.dto.response.QuotaInfoResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeAsyncResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeResultResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import com.interviewmaster.backend.mapper.InterviewSessionMapper;
import com.interviewmaster.backend.mapper.ResumeOptimizationMapper;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.entity.ResumeOptimization;
import com.interviewmaster.backend.model.entity.User;
import com.interviewmaster.backend.util.RedisUtil;
import com.interviewmaster.backend.util.ServiceUtils;
import com.interviewmaster.backend.model.dto.request.CreateInterviewSessionRequest;
import com.interviewmaster.backend.model.dto.request.ResumeOptimizeRequest;
import com.interviewmaster.backend.model.dto.request.FinishInterviewRequest;
import com.interviewmaster.backend.model.dto.request.SubmitInterviewAnswerRequest;
import com.interviewmaster.backend.model.dto.response.CreateInterviewSessionResponse;
import com.interviewmaster.backend.model.dto.response.FinishInterviewResponse;
import com.interviewmaster.backend.model.dto.response.InterviewSessionDetailResponse;
import com.interviewmaster.backend.model.dto.response.InterviewSessionMessageResponse;
import com.interviewmaster.backend.model.dto.response.SubmitInterviewAnswerResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    // 全局自增序列键，用于生成唯一 session/message ID。
    // 注意：这些 INCR key 故意不设 TTL——过期重置会导致 ID 碰撞。
    // 每个 key 仅占用几十字节，不存在内存泄漏风险。
    private static final String SESSION_ID_KEY = "interview:seq:session";
    private static final String MESSAGE_ID_KEY = "interview:seq:message";
    private static final String RESUME_FILE_KEY_PREFIX = "interview:resume:file:";
    private static final String SESSION_KEY_PREFIX = "interview:session:";
    private static final String SESSION_MESSAGES_KEY_SUFFIX = ":messages";
    private static final String ALGO_FLOW_STEPS_SEPARATOR = "||";
    private static final String STREAM_KEY_PREFIX = "interview:stream:";
    private static final int STREAM_EXPIRES_IN_SEC = 300;
    private static final Duration SESSION_TTL = Duration.ofDays(2);

    private static final ThreadPoolExecutor ASYNC_EXECUTOR = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20),
            new ThreadPoolExecutor.AbortPolicy());

    private final StringRedisTemplate stringRedisTemplate;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final DeepSeekService deepSeekService;
    private final ObjectMapper objectMapper;
    private final RedisUtil redisUtil;
    private final CheckinService checkinService;
    private final UserMapper userMapper;
    private final ResumeOptimizationMapper resumeOptimizationMapper;
    private final ApplicationContext applicationContext;

    // 创建模拟面试会话
    public CreateInterviewSessionResponse createSession(Long userId, CreateInterviewSessionRequest request) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        // 先校验参数，避免校验失败时已扣除每日次数
        String mode = normalizeMode(request.getMode());
        String style = normalizeStyle(request.getStyle());
        String algorithmDirection = trimToNull(request.getAlgorithmDirection());
        String jobTitle = resolveJobTitle(mode, request.getJobTitle(), algorithmDirection);
        String jdText = resolveJdText(mode, request.getJdText(), algorithmDirection);
        List<String> algoFlowSteps = resolveAlgoFlowSteps(mode, style, algorithmDirection);
        String resumeContext = resolveResumeContext(request);

        // 优先消耗管理员发放的额外配额（赠送额度），不占用每日基础次数
        boolean quotaExhausted = false;
        boolean consumedExtra = userMapper.decrementExtraInterviewQuota(userId) > 0;
        if (!consumedExtra) {
            // 额外配额不可用，使用每日基础额度
            String dailyKey = "interview:daily:" + userId + ":" + LocalDate.now();
            Long dailyCount = redisUtil.incrementWithExpiry(dailyKey, Duration.ofDays(1));
            int baseMaxDaily = checkinService.getContinuousDays(userId) >= 7 ? 7 : 6;
            if (dailyCount != null && dailyCount > baseMaxDaily) {
                quotaExhausted = true;
            }
        }
        Long sessionId = stringRedisTemplate.opsForValue().increment(SESSION_ID_KEY);
        Long messageId = stringRedisTemplate.opsForValue().increment(MESSAGE_ID_KEY);
        if (sessionId == null || messageId == null) {
            throw new BusinessException(500, "服务器内部错误");
        }
        LocalDateTime now = LocalDateTime.now();
        String createdAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String title = jobTitle + " - 模拟面试";
        String sessionCode = buildSessionCode(now, sessionId);
        String firstContent = buildFirstQuestion(mode, jobTitle, style, algoFlowSteps);
        try {
            String aiFirstContent = deepSeekService.generateOpeningQuestion(
                    jobTitle,
                    mode,
                    style,
                    jdText,
                    resumeContext
            );
            if (aiFirstContent != null && !aiFirstContent.isBlank()) {
                firstContent = aiFirstContent;
            }
        } catch (Exception ex) {
            log.warn("DeepSeek opening question generation failed, fallback to template. sessionId={}, reason={}",
                    sessionId, ex.getMessage());
        }
        String welcomeText = buildWelcomeText(mode, style);

        persistSession(userId, sessionId, sessionCode, title, mode, style, welcomeText, jdText, resumeContext,
                algorithmDirection, algoFlowSteps, now);
        persistFirstMessage(sessionId, messageId, firstContent, createdAt);

        CreateInterviewSessionResponse response = new CreateInterviewSessionResponse();
        response.setSessionId(sessionId);
        response.setSessionCode(sessionCode);
        response.setTitle(title);
        response.setMode(mode);
        response.setStatus("IN_PROGRESS");
        response.setQuotaExhausted(quotaExhausted);
        response.setWelcomeText(welcomeText);
        response.setAlgoFlow(buildAlgoFlow(mode, algoFlowSteps));
        response.setFirstMessage(buildFirstMessage(messageId, firstContent, createdAt));
        return response;
    }

    /**
     * 获取模拟面试会话详情
     *
     * @param userId 用户ID
     * @param sessionId 模拟面试会话ID
     * @return 模拟面试会话详情响应
     */
    public InterviewSessionDetailResponse getSessionInfo(Long userId, Long sessionId) {
        Map<Object, Object> sessionData = getSessionDataWithPermissionCheck(userId, sessionId);
        InterviewSessionDetailResponse response = new InterviewSessionDetailResponse();
        response.setSessionId(parseLong(sessionData.get("id")));
        response.setSessionCode(asString(sessionData.get("sessionCode")));
        response.setTitle(asString(sessionData.get("title")));
        String mode = asString(sessionData.get("mode"));
        response.setMode(mode);
        response.setStyle(asString(sessionData.get("style")));
        response.setStatus(asString(sessionData.get("status")));
        response.setWelcomeText(asString(sessionData.get("welcomeText")));
        response.setAlgoFlow(buildSessionDetailAlgoFlow(mode, sessionData.get("algoCurrentStep"), sessionData.get("algoFlowSteps")));
        response.setStartedAt(asString(sessionData.get("startedAt")));
        response.setFinishedAt(asString(sessionData.get("finishedAt")));
        return response;
    }

    public List<InterviewSessionMessageResponse> getSessionMessages(Long userId, Long sessionId, Long sinceId, Integer limit) {
        if (sinceId != null && sinceId < 0) {
            throw new BusinessException("参数错误：sinceId 非法");
        }
        int normalizedLimit = normalizeLimit(limit);
        try {
            return doGetSessionMessages(userId, sessionId, sinceId, normalizedLimit);
        } catch (Exception ex) {
            log.error("getSessionMessages 异常 sessionId={}: {}", sessionId, ex.getMessage(), ex);
            throw new BusinessException(500, "数据服务暂时不可用，请稍后重试");
        }
    }

    private List<InterviewSessionMessageResponse> doGetSessionMessages(Long userId, Long sessionId, Long sinceId, int normalizedLimit) {
        getSessionDataWithPermissionCheck(userId, sessionId);
        String messagesKey = buildSessionMessagesKey(sessionId);
        List<String> messageIds = stringRedisTemplate.opsForList().range(messagesKey, 0, -1);
        if (messageIds == null || messageIds.isEmpty()) {
            // Redis miss — fall back to MySQL
            List<Map<String, Object>> dbMessages = interviewMessageMapper.selectMessagesBySessionId(sessionId);
            if (dbMessages == null || dbMessages.isEmpty()) {
                return List.of();
            }
            // Rebuild Redis from MySQL and build response
            List<InterviewSessionMessageResponse> result = new ArrayList<>();
            for (Map<String, Object> dbMsg : dbMessages) {
                if (dbMsg == null) {
                    continue;
                }
                Long msgId = ServiceUtils.toLongOrNull(dbMsg.get("id"));
                if (msgId == null) {
                    continue;
                }
                if (sinceId != null && msgId <= sinceId) {
                    continue;
                }
                String msgRole = defaultString((String) dbMsg.get("role"), "assistant");
                String msgContent = defaultString((String) dbMsg.get("content"), "");
                Integer turnNo = ServiceUtils.toIntegerOrNull(dbMsg.get("turn_no"));
                Object createdAtRaw = dbMsg.get("created_at");
                String createdAt = createdAtRaw != null ? String.valueOf(createdAtRaw) : "";
                // Rebuild Redis message hash
                Map<String, String> payload = new HashMap<>();
                payload.put("id", String.valueOf(msgId));
                payload.put("sessionId", String.valueOf(sessionId));
                payload.put("role", msgRole);
                payload.put("kind", "user".equals(msgRole) ? "ANSWER" : "QUESTION");
                payload.put("turnNo", String.valueOf(turnNo == null ? 1 : turnNo));
                payload.put("content", msgContent);
                payload.put("contentFormat", "MARKDOWN");
                payload.put("typing", "false");
                payload.put("createdAt", createdAt);
                String msgKey = buildSessionMessageKey(sessionId, msgId);
                stringRedisTemplate.opsForHash().putAll(msgKey, payload);
                stringRedisTemplate.expire(msgKey, SESSION_TTL);
                stringRedisTemplate.opsForList().rightPush(messagesKey, String.valueOf(msgId));
                // Build response item
                InterviewSessionMessageResponse item = new InterviewSessionMessageResponse();
                item.setId(msgId);
                item.setRole(msgRole);
                item.setTurnNo(turnNo == null ? 1 : turnNo);
                item.setContent(msgContent);
                item.setContentFormat("MARKDOWN");
                item.setTyping(false);
                item.setCreatedAt(createdAt);
                result.add(item);
            }
            stringRedisTemplate.expire(messagesKey, SESSION_TTL);
            if (result.size() > normalizedLimit) {
                result = result.subList(result.size() - normalizedLimit, result.size());
            }
            return result;
        }
        // Redis hit — refresh TTL
        stringRedisTemplate.expire(messagesKey, SESSION_TTL);
        List<InterviewSessionMessageResponse> result = new ArrayList<>();
        for (int i = messageIds.size() - 1; i >= 0 && result.size() < normalizedLimit; i--) {
            Long messageId = parseLong(messageIds.get(i));
            if (messageId == null) {
                continue;
            }
            if (sinceId != null && messageId <= sinceId) {
                continue;
            }
            Map<Object, Object> messageData = stringRedisTemplate.opsForHash().entries(buildSessionMessageKey(sessionId, messageId));
            if (messageData == null || messageData.isEmpty()) {
                continue;
            }
            result.add(buildSessionMessageResponse(messageData, messageId));
        }
        Collections.reverse(result);
        return result;
    }

    public SubmitInterviewAnswerResponse submitAnswer(Long userId, Long sessionId, SubmitInterviewAnswerRequest request) {
        Map<Object, Object> sessionData = getSessionDataWithPermissionCheck(userId, sessionId);
        String status = asString(sessionData.get("status"));
        if ("FINISHED".equals(status)) {
            throw new BusinessException("会话已结束，不能继续作答");
        }
        String answerContent = trimToNull(request.getContent());
        if (answerContent == null) {
            throw new BusinessException("参数错误：content 不能为空");
        }
        Integer requestTurnNo = request.getTurnNo();
        if (requestTurnNo == null || requestTurnNo < 1) {
            throw new BusinessException("参数错误：turnNo 非法");
        }
        Integer currentTurnNo = parseInteger(sessionData.get("currentTurn"));
        if (currentTurnNo == null || currentTurnNo < 1) {
            currentTurnNo = 1;
        }
        if (!requestTurnNo.equals(currentTurnNo)) {
            throw new BusinessException("轮次不匹配，请刷新会话后重试");
        }
        // Acquire turn-level lock to prevent concurrent submissions for the same turn
        String turnLockKey = SESSION_KEY_PREFIX + sessionId + ":turn-lock:" + currentTurnNo;
        Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(turnLockKey, "1", Duration.ofSeconds(30));
        if (lockAcquired == null || !lockAcquired) {
            throw new BusinessException("该轮次正在处理中，请稍候重试");
        }
        String mode = asString(sessionData.get("mode"));
        String style = asString(sessionData.get("style"));
        List<String> algoFlowSteps = parseAlgoFlowSteps(sessionData.get("algoFlowSteps"));
        LocalDateTime now = LocalDateTime.now();
        String nowText = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Long userAnswerId = nextMessageId();
        persistMessage(sessionId, userAnswerId, "user", "ANSWER", currentTurnNo, answerContent, null, null, false, nowText);
        Integer updatedAlgoStep = parseInteger(sessionData.get("algoCurrentStep"));
        if (updatedAlgoStep == null || updatedAlgoStep < 1) {
            updatedAlgoStep = 1;
        }
        String reviewType = decideReviewType(answerContent);
        Integer nextTurnNo = currentTurnNo + 1;
        boolean isFinished;
        if ("ALGORITHM".equals(mode)) {
            int totalAlgoSteps = algoFlowSteps.isEmpty() ? 5 : algoFlowSteps.size();
            isFinished = updatedAlgoStep >= totalAlgoSteps;
            if (!isFinished) {
                updatedAlgoStep = updatedAlgoStep + 1;
            }
        } else {
            isFinished = currentTurnNo >= 6;
        }
        String reviewContent = buildReviewContent(mode, style, currentTurnNo, answerContent, reviewType);
        String followupContent = buildFollowupContent(mode, style, nextTurnNo, updatedAlgoStep, isFinished, algoFlowSteps);
        try {
            try {
            String jobTitle = asString(sessionData.get("title"));
            String jdText = asString(sessionData.get("jdText"));
            String resumeContext = asString(sessionData.get("resumeContext"));
            String conversationHistory = buildConversationHistory(userId, sessionId);
            DeepSeekService.ReviewFollowupResult aiResult = deepSeekService.generateReviewAndFollowup(
                    jobTitle,
                    mode,
                    style,
                    currentTurnNo,
                    jdText,
                    resumeContext,
                    answerContent,
                    updatedAlgoStep,
                    algoFlowSteps,
                    conversationHistory
            );
            if (aiResult == null) {
                throw new BusinessException(500, "DeepSeek 返回空结果");
            }
            String aiReviewType = normalizeReviewType(aiResult.getReviewType());
            if (aiReviewType != null) {
                reviewType = aiReviewType;
            }
            String aiReviewContent = trimToNull(aiResult.getReviewContent());
            if (aiReviewContent != null) {
                reviewContent = aiReviewContent;
            }
            String aiFollowupContent = trimToNull(aiResult.getFollowupContent());
            if (aiFollowupContent != null) {
                followupContent = aiFollowupContent;
            }
            if ("ALGORITHM".equals(mode)) {
                Integer aiNextAlgoStep = aiResult.getNextAlgoStep();
                if (aiNextAlgoStep != null && aiNextAlgoStep >= 1) {
                    updatedAlgoStep = aiNextAlgoStep;
                }
            }
            if (aiResult.getFinished() != null) {
                // 综合面试模式：轮次上限由后端控制，不允许 AI 改写 finished 状态
                if (!"ALGORITHM".equals(mode)) {
                    isFinished = isFinished || aiResult.getFinished();
                } else {
                    isFinished = aiResult.getFinished();
                }
            }
        } catch (Exception ex) {
            log.warn("DeepSeek review/followup generation failed, fallback to template. sessionId={}, turnNo={}, reason={}",
                    sessionId, currentTurnNo, ex.getMessage());
        }
        if ("ALGORITHM".equals(mode)) {
            int totalAlgoSteps = algoFlowSteps.isEmpty() ? 5 : algoFlowSteps.size();
            if (updatedAlgoStep < 1) {
                updatedAlgoStep = 1;
            } else if (updatedAlgoStep > totalAlgoSteps) {
                updatedAlgoStep = totalAlgoSteps;
            }
            if (isFinished) {
                updatedAlgoStep = totalAlgoSteps;
            }
        } else if (isFinished) {
            followupContent = "本场模拟面试已完成。建议你基于本次问答回顾关键失分点，并整理成可复用的答题模板。";
        }
        if (shouldForceWeakAnswerReview(answerContent)) {
            reviewType = "IMPROVE";
            reviewContent = buildWeakAnswerReviewContent(mode, currentTurnNo, answerContent);
            if (!isFinished) {
                followupContent = buildWeakAnswerFollowupContent(mode, style, nextTurnNo, updatedAlgoStep, algoFlowSteps);
            }
        }
        String assistantContent = buildAssistantMarkdownContent(reviewContent, followupContent, isFinished);
        Long assistantMessageId = nextMessageId();
        persistMessage(sessionId, assistantMessageId, "assistant", "ASSISTANT", nextTurnNo, assistantContent, null, reviewType, false, nowText);

        updateSessionProgress(sessionId, nextTurnNo, updatedAlgoStep, isFinished, nowText, mode);
        if (isFinished) {
            syncFinishedSessionToDatabase(sessionId, sessionData, nowText);
        }

        SubmitInterviewAnswerResponse response = new SubmitInterviewAnswerResponse();
        response.setAssistantMessage(buildAssistantMessage(assistantMessageId, nextTurnNo, assistantContent, false, nowText));
        response.setAlgoFlow(buildSubmitAnswerAlgoFlow(mode, updatedAlgoStep, algoFlowSteps));
        response.setIsFinished(isFinished);
        return response;
        } finally {
            stringRedisTemplate.delete(turnLockKey);
        }
    }

    public SseEmitter streamAnswer(Long userId, Long sessionId, SubmitInterviewAnswerRequest request) {
        Map<Object, Object> sessionData = getSessionDataWithPermissionCheck(userId, sessionId);
        String status = asString(sessionData.get("status"));
        if ("FINISHED".equals(status)) {
            throw new BusinessException("会话已结束，不能继续作答");
        }
        String answerContent = trimToNull(request.getContent());
        if (answerContent == null) {
            throw new BusinessException("参数错误：content 不能为空");
        }
        Integer requestTurnNo = request.getTurnNo();
        if (requestTurnNo == null || requestTurnNo < 1) {
            throw new BusinessException("参数错误：turnNo 非法");
        }
        Integer rawTurn = parseInteger(sessionData.get("currentTurn"));
        final Integer currentTurnNo = (rawTurn != null && rawTurn >= 1) ? rawTurn : 1;
        if (!requestTurnNo.equals(currentTurnNo)) {
            throw new BusinessException("轮次不匹配，请刷新会话后重试");
        }

        // 幂等性检查：使用 requestId + turnNo 作为唯一标识
        String requestId = trimToNull(request.getRequestId());
        if (requestId != null) {
            String idempotencyKey = SESSION_KEY_PREFIX + sessionId + ":idempotency:" + currentTurnNo + ":" + requestId;
            Boolean alreadyProcessed = stringRedisTemplate.hasKey(idempotencyKey);
            if (Boolean.TRUE.equals(alreadyProcessed)) {
                throw new BusinessException("请求已处理，请勿重复提交");
            }
        }

        // Acquire turn-level lock to prevent concurrent submissions for the same turn
        String turnLockKey = SESSION_KEY_PREFIX + sessionId + ":turn-lock:" + currentTurnNo;
        Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(turnLockKey, "1", Duration.ofSeconds(30));
        if (lockAcquired == null || !lockAcquired) {
            throw new BusinessException("该轮次正在处理中，请稍候重试");
        }

        try {
            // 设置幂等性 key，防止快速重试产生重复消息
            if (requestId != null) {
                String idempotencyKey = SESSION_KEY_PREFIX + sessionId + ":idempotency:" + currentTurnNo + ":" + requestId;
                stringRedisTemplate.opsForValue().set(idempotencyKey, "1", Duration.ofMinutes(5));
            }

            final String mode = asString(sessionData.get("mode"));
            final String style = asString(sessionData.get("style"));
            final List<String> algoFlowSteps = parseAlgoFlowSteps(sessionData.get("algoFlowSteps"));
            final String jobTitle = asString(sessionData.get("title"));
            final String jdText = asString(sessionData.get("jdText"));
            final String resumeContext = asString(sessionData.get("resumeContext"));
            boolean pressure = "PRESSURE".equalsIgnoreCase(style);
            Integer rawAlgoStep = parseInteger(sessionData.get("algoCurrentStep"));
            final Integer updatedAlgoStep = (rawAlgoStep != null && rawAlgoStep >= 1) ? rawAlgoStep : 1;
            final int totalAlgoSteps = algoFlowSteps.isEmpty() ? 5 : algoFlowSteps.size();
            final String stepsText = algoFlowSteps.isEmpty() ? "无" : String.join(" -> ", algoFlowSteps);

            LocalDateTime now = LocalDateTime.now();
            final String nowText = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            Long userAnswerId = nextMessageId();
            persistMessage(sessionId, userAnswerId, "user", "ANSWER", currentTurnNo, answerContent, null, null, false, nowText);

            String conversationHistory = buildConversationHistory(userId, sessionId);
            String systemPrompt = buildStreamingSystemPrompt(mode, pressure);
            String userPrompt = buildStreamingUserPrompt(jobTitle, mode, style, currentTurnNo,
                    updatedAlgoStep, totalAlgoSteps, stepsText, jdText, resumeContext, answerContent,
                    conversationHistory);

            SseEmitter emitter = new SseEmitter(120000L);
            StringBuilder fullContent = new StringBuilder();
            AtomicBoolean completed = new AtomicBoolean(false);

            log.info("Starting DeepSeek streaming call. sessionId={}, turnNo={}, model={}",
                    sessionId, currentTurnNo, deepSeekService.getModelName());

            Disposable disposable = deepSeekService.streamRawCompletion(systemPrompt, userPrompt, 0.7)
                    .delayElements(Duration.ofMillis(30))
                    .subscribe(
                            rawLine -> {
                                if (completed.get()) {
                                    return;
                                }
                                if ("[DONE]".equals(rawLine)) {
                                    if (completed.compareAndSet(false, true)) {
                                        log.info("DeepSeek streaming completed [DONE]. sessionId={}, turnNo={}, contentLength={}",
                                                sessionId, currentTurnNo, fullContent.length());
                                        Integer nextTurnNo = currentTurnNo + 1;
                                        boolean isAlgoMode = "ALGORITHM".equals(mode);
                                        Integer nextAlgoStep = isAlgoMode ? Math.min(updatedAlgoStep + 1, totalAlgoSteps) : updatedAlgoStep;
                                        finishStreamedMessage(emitter, sessionId, sessionData, fullContent.toString(),
                                                nextTurnNo, nextAlgoStep, isAlgoMode, algoFlowSteps, nowText, mode,
                                                currentTurnNo, answerContent);
                                    }
                                } else {
                                    String delta = extractDeltaContent(rawLine);
                                    if (delta != null && !delta.isEmpty()) {
                                        fullContent.append(delta);
                                        try {
                                            emitter.send(SseEmitter.event().name("delta").data(Map.of("content", delta)));
                                        } catch (Exception ex) {
                                            log.warn("Failed to send delta SSE event. sessionId={}", sessionId, ex);
                                        }
                                    }
                                }
                            },
                            error -> {
                                log.warn("DeepSeek streaming error. sessionId={}, turnNo={}, errorClass={}, message={}",
                                        sessionId, currentTurnNo, error.getClass().getName(), error.getMessage(), error);
                                if (completed.compareAndSet(false, true)) {
                                    handleStreamError(emitter, sessionId, sessionData, fullContent.toString(),
                                            currentTurnNo, updatedAlgoStep, mode, algoFlowSteps, nowText, answerContent, error);
                                }
                            },
                            () -> {
                                if (completed.compareAndSet(false, true)) {
                                    log.info("DeepSeek streaming onComplete (no [DONE]). sessionId={}, turnNo={}, contentLength={}",
                                            sessionId, currentTurnNo, fullContent.length());
                                    Integer nextTurnNo = currentTurnNo + 1;
                                    boolean isAlgoMode = "ALGORITHM".equals(mode);
                                    Integer nextAlgoStep = isAlgoMode ? Math.min(updatedAlgoStep + 1, totalAlgoSteps) : updatedAlgoStep;
                                    finishStreamedMessage(emitter, sessionId, sessionData, fullContent.toString(),
                                            nextTurnNo, nextAlgoStep, isAlgoMode, algoFlowSteps, nowText, mode,
                                            currentTurnNo, answerContent);
                                }
                            }
                    );

            emitter.onCompletion(() -> {
                if (!completed.get()) {
                    log.info("SSE client disconnected (onCompletion). Canceling upstream. sessionId={}, turnNo={}",
                            sessionId, currentTurnNo);
                    disposable.dispose();
                }
            });
            emitter.onError(ex -> {
                if (!completed.get()) {
                    log.info("SSE client disconnected (onError). Canceling upstream. sessionId={}, turnNo={}",
                            sessionId, currentTurnNo);
                    disposable.dispose();
                }
            });
            emitter.onTimeout(() -> {
                if (completed.compareAndSet(false, true)) {
                    handleStreamTimeout(emitter, sessionId, sessionData, fullContent.toString(),
                            currentTurnNo, updatedAlgoStep, mode, algoFlowSteps, nowText, answerContent);
                }
                disposable.dispose();
            });

            return emitter;
        } catch (Exception ex) {
            stringRedisTemplate.delete(turnLockKey);
            throw ex;
        }
    }

    public FinishInterviewResponse finishSession(Long userId, Long sessionId, FinishInterviewRequest request) {
        Map<Object, Object> sessionData = getSessionDataWithPermissionCheck(userId, sessionId);
        String existedFinishedAt = asString(sessionData.get("finishedAt"));
        String status = asString(sessionData.get("status"));
        String finishedAt;
        if ("FINISHED".equals(status) && existedFinishedAt != null && !existedFinishedAt.isBlank()) {
            finishedAt = existedFinishedAt;
        } else {
            // Prevent duplicate async evaluations from concurrent finishSession calls
            String finishLockKey = SESSION_KEY_PREFIX + sessionId + ":finish_lock";
            Boolean finishLocked = stringRedisTemplate.opsForValue().setIfAbsent(finishLockKey, "1", Duration.ofSeconds(10));
            if (finishLocked == null || !finishLocked) {
                Object currentFinishedAt = stringRedisTemplate.opsForHash().get(SESSION_KEY_PREFIX + sessionId, "finishedAt");
                FinishInterviewResponse response = new FinishInterviewResponse();
                response.setSessionId(sessionId);
                response.setStatus("FINISHED");
                response.setFinishedAt(currentFinishedAt != null ? currentFinishedAt.toString() : null);
                return response;
            }
            // Double-check: status may have been set to FINISHED by a concurrent request
            Object currentStatus = stringRedisTemplate.opsForHash().get(SESSION_KEY_PREFIX + sessionId, "status");
            if ("FINISHED".equals(currentStatus)) {
                finishedAt = asString(stringRedisTemplate.opsForHash().get(SESSION_KEY_PREFIX + sessionId, "finishedAt"));
                if (finishedAt == null || finishedAt.isBlank()) {
                    finishedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            } else {
                LocalDateTime now = LocalDateTime.now();
                finishedAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                Map<String, String> payload = new HashMap<>();
                payload.put("status", "FINISHED");
                payload.put("finishedAt", finishedAt);
                payload.put("updatedAt", finishedAt);
                String reason = request == null ? null : trimToNull(request.getReason());
                if (reason != null) {
                    payload.put("finishReason", reason);
                }
                stringRedisTemplate.opsForHash().putAll(SESSION_KEY_PREFIX + sessionId, payload);
                Integer turnNo = parseInteger(sessionData.get("currentTurn"));
                if (turnNo == null || turnNo < 1) {
                    turnNo = 1;
                }
                Long messageId = nextMessageId();
                String content = reason == null ? "本场模拟面试已结束。" : "本场模拟面试已结束。结束原因：" + reason;
                persistMessage(sessionId, messageId, "system", "SYSTEM", turnNo, content, null, null, false, finishedAt);
            }
        }
        syncFinishedSessionToDatabase(sessionId, sessionData, finishedAt);

        // Trigger async evaluation — fire and forget, result persisted to Redis + MySQL
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    evaluate(userId, sessionId);
                } catch (Exception ex) {
                    log.warn("Async evaluation failed for sessionId={}", sessionId, ex);
                }
            }, ASYNC_EXECUTOR);
        } catch (RejectedExecutionException ex) {
            log.warn("Async evaluation rejected (pool full), sessionId={}", sessionId);
        }

        FinishInterviewResponse response = new FinishInterviewResponse();
        response.setSessionId(sessionId);
        response.setStatus("FINISHED");
        response.setFinishedAt(finishedAt);
        return response;
    }

    public EvaluateInterviewResponse evaluate(Long userId, Long sessionId) {
        Map<Object, Object> sessionData = getSessionDataWithPermissionCheck(userId, sessionId);

        // Return cached evaluation if already exists
        Object cachedJson = sessionData.get("evaluation");
        if (cachedJson != null && !cachedJson.toString().isBlank()) {
            try {
                EvaluateInterviewResponse cached = objectMapper.readValue(cachedJson.toString(),
                        EvaluateInterviewResponse.class);
                if (cached != null && cached.getTotalScore() != null) {
                    return cached;
                }
            } catch (Exception ex) {
                log.warn("Failed to deserialize cached evaluation, will regenerate. sessionId={}", sessionId);
            }
        }

        // Prevent concurrent evaluation — only one worker generates, others wait and re-read
        String lockKey = SESSION_KEY_PREFIX + sessionId + ":eval_lock";
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(120));
        if (acquired == null || !acquired) {
            throw new BusinessException(202, "评估正在生成中，请稍后再查看");
        }
        try {
            String mode = asString(sessionData.get("mode"));
            String jobTitle = asString(sessionData.get("title"));
            String resumeContext = asString(sessionData.get("resumeContext"));

            List<InterviewSessionMessageResponse> allMessages = getSessionMessages(userId, sessionId, null, 100);
            StringBuilder conversationBuilder = new StringBuilder();
            for (InterviewSessionMessageResponse msg : allMessages) {
                String role = msg.getRole();
                if ("system".equals(role)) {
                    continue;
                }
                String label = "user".equals(role) ? "候选人" : "面试官";
                conversationBuilder.append("[").append(label).append(" 第").append(msg.getTurnNo()).append("轮] ")
                        .append(defaultString(msg.getContent(), "")).append("\n\n");
            }
            String conversationText = conversationBuilder.toString().trim();
            if (conversationText.isEmpty()) {
                conversationText = "（无对话记录）";
            }

            EvaluateInterviewResponse evaluation = deepSeekService.evaluateInterview(mode, conversationText, jobTitle, resumeContext);
            if (evaluation == null) {
                throw new BusinessException(500, "AI 评分失败，请稍后重试");
            }
            if (evaluation.getTotalScore() == null) {
                evaluation.setTotalScore(0);
            }

            // Persist to Redis
            try {
                String json = objectMapper.writeValueAsString(evaluation);
                stringRedisTemplate.opsForHash().put(SESSION_KEY_PREFIX + sessionId, "evaluation", json);
            } catch (Exception ex) {
                log.warn("Failed to save evaluation to Redis. sessionId={}", sessionId, ex);
            }

            // Persist to MySQL
            try {
                String json = objectMapper.writeValueAsString(evaluation);
                interviewSessionMapper.upsertEvaluation(sessionId, json);
            } catch (Exception ex) {
                log.warn("Failed to save evaluation to MySQL. sessionId={}", sessionId, ex);
            }

            return evaluation;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    public List<InterviewReportListResponse.ReportItem> getTrendScores(Long userId, int limit) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 30);
        List<Map<String, Object>> rows = interviewSessionMapper.listLatestScoredSessions(userId, safeLimit);
        List<InterviewReportListResponse.ReportItem> items = new ArrayList<>();
        if (rows != null) {
            java.util.Collections.reverse(rows);
            for (Map<String, Object> row : rows) {
                InterviewReportListResponse.ReportItem item = new InterviewReportListResponse.ReportItem();
                item.setSessionId(ServiceUtils.toLongOrNull(row.get("id")));
                item.setTitle(asString(row.get("title")));
                item.setMode(difficultyToModeString(ServiceUtils.toIntegerOrNull(row.get("difficulty"))));
                item.setStyle(focusToStyleString(ServiceUtils.toIntegerOrNull(row.get("focus"))));
                item.setTotalScore(extractTotalScore(asString(row.get("evaluation"))));
                item.setFinishedAt(asString(row.get("finished_at")));
                items.add(item);
            }
        }
        return items;
    }

    public ResumeOptimizeResponse optimizeResume(Long userId, ResumeOptimizeRequest request) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        // 优先消耗管理员发放的额外配额（赠送额度），不占用每日基础次数
        boolean consumedExtra = userMapper.decrementExtraResumeQuota(userId) > 0;
        String dailyKey = null;
        if (!consumedExtra) {
            // 额外配额不可用，使用每日基础额度
            dailyKey = "resume:daily:" + userId + ":" + LocalDate.now();
            Long dailyCount = redisUtil.incrementWithExpiry(dailyKey, Duration.ofDays(1));
            int baseMaxDaily = 5;
            if (dailyCount != null && dailyCount > baseMaxDaily) {
                try {
                    stringRedisTemplate.opsForValue().decrement(dailyKey);
                } catch (Exception ignored) {
                }
                throw new BusinessException("今日简历优化次数已用完（每天最多" + baseMaxDaily + "次），请明天再来");
            }
        }
        String resumeText = request.getResumeText();
        if (resumeText == null || resumeText.isBlank()) {
            if (!consumedExtra && dailyKey != null) {
                stringRedisTemplate.opsForValue().decrement(dailyKey);
            }
            throw new BusinessException(400, "简历内容不能为空");
        }

        // Gather interview insights from recent scored sessions
        List<String> interviewInsights = new ArrayList<>();
        try {
            List<Map<String, Object>> recentSessions =
                    interviewSessionMapper.listLatestScoredSessions(userId, 5);
            if (recentSessions != null) {
                for (Map<String, Object> row : recentSessions) {
                    String evaluationJson = asString(row.get("evaluation"));
                    if (evaluationJson != null && !evaluationJson.isBlank()) {
                        Map<String, Object> eval = objectMapper.readValue(evaluationJson,
                                new TypeReference<Map<String, Object>>() {});
                        String improvements = asString(eval.get("improvements"));
                        String summary = asString(eval.get("summary"));
                        if (improvements != null && !improvements.isBlank()) {
                            String title = asString(row.get("title"));
                            String prefix = title != null ? "[" + title + "面试] " : "";
                            interviewInsights.add(prefix + "改进方向：" + improvements);
                        } else if (summary != null && !summary.isBlank()) {
                            String title = asString(row.get("title"));
                            String prefix = title != null ? "[" + title + "面试] " : "";
                            interviewInsights.add(prefix + summary);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to gather interview insights for resume optimization. userId={}", userId, e);
        }

        ResumeOptimizeResponse result = deepSeekService.optimizeResume(
                resumeText, request.getJobDescription(), interviewInsights);

        if (result == null) {
            throw new BusinessException(500, "AI 简历分析失败，请稍后重试");
        }
        if (result.getOverallScore() == null) {
            result.setOverallScore(0);
        }
        return result;
    }

    public ResumeOptimizeAsyncResponse optimizeResumeAsync(Long userId, ResumeOptimizeRequest request) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        // 优先消耗管理员发放的额外配额（赠送额度），不占用每日基础次数
        boolean consumedExtra = userMapper.decrementExtraResumeQuota(userId) > 0;
        String dailyKey = null;
        if (!consumedExtra) {
            // 额外配额不可用，使用每日基础额度
            dailyKey = "resume:daily:" + userId + ":" + LocalDate.now();
            Long dailyCount = redisUtil.incrementWithExpiry(dailyKey, Duration.ofDays(1));
            int baseMaxDaily = 5;
            if (dailyCount != null && dailyCount > baseMaxDaily) {
                try {
                    stringRedisTemplate.opsForValue().decrement(dailyKey);
                } catch (Exception ignored) {
                }
                throw new BusinessException("今日简历优化次数已用完（每天最多" + baseMaxDaily + "次），请明天再来");
            }
        }
        String resumeText = request.getResumeText();
        if (resumeText == null || resumeText.isBlank()) {
            if (!consumedExtra && dailyKey != null) {
                stringRedisTemplate.opsForValue().decrement(dailyKey);
            }
            throw new BusinessException(400, "简历内容不能为空");
        }

        // Gather interview insights from recent scored sessions
        List<String> interviewInsights = new ArrayList<>();
        try {
            List<Map<String, Object>> recentSessions =
                    interviewSessionMapper.listLatestScoredSessions(userId, 5);
            if (recentSessions != null) {
                for (Map<String, Object> row : recentSessions) {
                    String evaluationJson = asString(row.get("evaluation"));
                    if (evaluationJson != null && !evaluationJson.isBlank()) {
                        Map<String, Object> eval = objectMapper.readValue(evaluationJson,
                                new TypeReference<Map<String, Object>>() {});
                        String improvements = asString(eval.get("improvements"));
                        String summary = asString(eval.get("summary"));
                        if (improvements != null && !improvements.isBlank()) {
                            String title = asString(row.get("title"));
                            String prefix = title != null ? "[" + title + "面试] " : "";
                            interviewInsights.add(prefix + "改进方向：" + improvements);
                        } else if (summary != null && !summary.isBlank()) {
                            String title = asString(row.get("title"));
                            String prefix = title != null ? "[" + title + "面试] " : "";
                            interviewInsights.add(prefix + summary);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to gather interview insights for resume optimization. userId={}", userId, e);
        }

        String taskId = "ro_" + userId + "_" + UUID.randomUUID().toString().replace("-", "");

        // Persist to DB so result survives beyond Redis TTL and page reloads
        ResumeOptimization record = new ResumeOptimization();
        record.setUserId(userId);
        record.setTaskId(taskId);
        record.setResumeText(resumeText);
        record.setJobDescription(request.getJobDescription());
        record.setStatus("PROCESSING");
        resumeOptimizationMapper.insert(record);

        // Cache initial status in Redis for fast polling
        ResumeOptimizeResultResponse initial = new ResumeOptimizeResultResponse();
        initial.setStatus("PROCESSING");
        stringRedisTemplate.opsForValue().set(
                "resume:optimize:result:" + userId + ":" + taskId,
                serializeJson(initial),
                Duration.ofMinutes(30));

        // Must go through Spring proxy for @Async to work — direct same-class call bypasses AOP.
        applicationContext.getBean(InterviewService.class)
                .processResumeOptimizeAsync(taskId, userId, resumeText, request.getJobDescription(), interviewInsights);

        ResumeOptimizeAsyncResponse response = new ResumeOptimizeAsyncResponse();
        response.setTaskId(taskId);
        return response;
    }

    @Async("resumeOptimizeExecutor")
    public void processResumeOptimizeAsync(String taskId, Long userId, String resumeText,
                                           String jobDescription, List<String> interviewInsights) {
        String resultKey = "resume:optimize:result:" + userId + ":" + taskId;
        try {
            ResumeOptimizeResponse result = deepSeekService.optimizeResume(
                    resumeText, jobDescription, interviewInsights);
            if (result == null) {
                throw new RuntimeException("AI 简历分析返回空结果");
            }
            if (result.getOverallScore() == null) {
                result.setOverallScore(0);
            }
            ResumeOptimizeResultResponse done = new ResumeOptimizeResultResponse();
            done.setStatus("DONE");
            done.setResult(result);
            String resultJson = serializeJson(done);
            stringRedisTemplate.opsForValue().set(resultKey, resultJson, Duration.ofMinutes(30));
            resumeOptimizationMapper.updateResultByTaskId(taskId, "DONE", resultJson, null);
        } catch (Exception e) {
            log.error("Async resume optimization failed. taskId={}, userId={}", taskId, userId, e);
            ResumeOptimizeResultResponse failed = new ResumeOptimizeResultResponse();
            failed.setStatus("FAILED");
            failed.setErrorMessage(e.getMessage());
            String resultJson = serializeJson(failed);
            stringRedisTemplate.opsForValue().set(resultKey, resultJson, Duration.ofMinutes(30));
            resumeOptimizationMapper.updateResultByTaskId(taskId, "FAILED", resultJson, e.getMessage());
        }
    }

    public ResumeOptimizeResultResponse getOptimizeResult(Long userId, String taskId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        // Try Redis first for fast polling
        String resultKey = "resume:optimize:result:" + userId + ":" + taskId;
        String json = stringRedisTemplate.opsForValue().get(resultKey);
        if (json != null && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, ResumeOptimizeResultResponse.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize optimize result from Redis. taskId={}", taskId, e);
            }
        }
        // Fallback to DB in case Redis expired
        ResumeOptimization record = resumeOptimizationMapper.selectLatestByUserId(userId);
        if (record != null && taskId.equals(record.getTaskId()) && record.getResultJson() != null) {
            try {
                return objectMapper.readValue(record.getResultJson(), ResumeOptimizeResultResponse.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize optimize result from DB. taskId={}", taskId, e);
            }
        }
        ResumeOptimizeResultResponse notFound = new ResumeOptimizeResultResponse();
        notFound.setStatus("NOT_FOUND");
        return notFound;
    }

    public ResumeOptimizeResultResponse getLatestOptimization(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        ResumeOptimization record = resumeOptimizationMapper.selectLatestByUserId(userId);
        if (record == null) {
            return null;
        }
        ResumeOptimizeResultResponse response = new ResumeOptimizeResultResponse();
        response.setStatus(record.getStatus());
        response.setResumeText(record.getResumeText());
        response.setJobDescription(record.getJobDescription());
        if (record.getResultJson() != null && !record.getResultJson().isBlank()) {
            try {
                ResumeOptimizeResultResponse parsed = objectMapper.readValue(
                        record.getResultJson(), ResumeOptimizeResultResponse.class);
                response.setResult(parsed.getResult());
                response.setErrorMessage(parsed.getErrorMessage());
            } catch (Exception e) {
                log.warn("Failed to parse stored result. recordId={}", record.getId(), e);
                response.setStatus("FAILED");
                response.setErrorMessage("结果解析失败");
            }
        }
        return response;
    }

    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    public QuotaInfoResponse getResumeQuota(Long userId) {
        String dailyKey = "resume:daily:" + userId + ":" + LocalDate.now();
        String usedStr = stringRedisTemplate.opsForValue().get(dailyKey);
        int used = 0;
        if (usedStr != null) {
            try {
                used = Integer.parseInt(usedStr);
            } catch (NumberFormatException ignored) {
            }
        }
        int baseMaxDaily = 5;
        User user = userMapper.selectById(userId);
        int extraQuota = (user != null && user.getExtraResumeQuota() != null) ? user.getExtraResumeQuota() : 0;
        QuotaInfoResponse response = new QuotaInfoResponse();
        response.setRemainingResumeOptimizations(Math.max(0, baseMaxDaily - used) + extraQuota);
        response.setMaxResumeOptimizations(baseMaxDaily);
        return response;
    }

    public InterviewReportListResponse listReports(Long userId, Integer page, Integer size, String mode) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        int pageNum = ServiceUtils.pageOrDefault(page);
        int pageSize = ServiceUtils.pageSizeOrDefault(size);
        String normalizedMode = normalizeReportMode(mode);
        Integer difficulty = modeToDifficulty(normalizedMode);
        long total = interviewSessionMapper.countCompletedSessions(userId, normalizedMode, difficulty);
        int offset = (pageNum - 1) * pageSize;
        List<Map<String, Object>> rows = interviewSessionMapper.listCompletedSessions(userId, normalizedMode, difficulty, offset, pageSize);
        List<InterviewReportListResponse.ReportItem> items = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                InterviewReportListResponse.ReportItem item = new InterviewReportListResponse.ReportItem();
                item.setSessionId(ServiceUtils.toLongOrNull(row.get("id")));
                item.setTitle(asString(row.get("title")));
                item.setMode(difficultyToModeString(ServiceUtils.toIntegerOrNull(row.get("difficulty"))));
                item.setStyle(focusToStyleString(ServiceUtils.toIntegerOrNull(row.get("focus"))));
                item.setTotalScore(extractTotalScore(asString(row.get("evaluation"))));
                item.setFinishedAt(asString(row.get("finished_at")));
                items.add(item);
            }
        }
        InterviewReportListResponse response = new InterviewReportListResponse();
        response.setList(items);
        response.setPage(pageNum);
        response.setSize(pageSize);
        response.setTotal(ServiceUtils.safeInt(total));
        return response;
    }

    public EvaluateInterviewResponse getReportDetail(Long userId, Long sessionId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException("参数错误：sessionId 非法");
        }
        Map<String, Object> row = interviewSessionMapper.selectSessionById(sessionId);
        if (row == null || row.isEmpty()) {
            throw new BusinessException(404, "面试记录不存在");
        }
        Long ownerId = ServiceUtils.toLongOrNull(row.get("user_id"));
        if (!userId.equals(ownerId)) {
            throw new BusinessException(403, "无权查看该面试记录");
        }
        String evaluationJson = asString(row.get("evaluation"));
        if (evaluationJson == null || evaluationJson.isBlank()) {
            throw new BusinessException(40401, "该面试尚未生成评估报告");
        }
        try {
            return objectMapper.readValue(evaluationJson, EvaluateInterviewResponse.class);
        } catch (Exception ex) {
            throw new BusinessException(500, "评估报告解析失败");
        }
    }

    public byte[] generateReportPdf(Long userId, Long sessionId) {
        if (userId == null) throw new BusinessException(401, "未登录");
        if (sessionId == null || sessionId <= 0) throw new BusinessException("参数错误：sessionId 非法");

        Map<String, Object> row = interviewSessionMapper.selectSessionById(sessionId);
        if (row == null || row.isEmpty()) throw new BusinessException(404, "面试记录不存在");
        Long ownerId = ServiceUtils.toLongOrNull(row.get("user_id"));
        if (!userId.equals(ownerId)) throw new BusinessException(403, "无权查看该面试记录");
        String title = asString(row.get("title"));
        if (title == null || title.isBlank()) title = "面试报告";
        String mode = difficultyToModeString(ServiceUtils.toIntegerOrNull(row.get("difficulty")));
        String style = focusToStyleString(ServiceUtils.toIntegerOrNull(row.get("focus")));
        String date = asString(row.get("started_at"));
        if (date != null && date.contains("T")) {
            date = date.substring(0, date.indexOf('T'));
        }

        String evaluationJson = asString(row.get("evaluation"));
        EvaluateInterviewResponse ev = null;
        if (evaluationJson != null && !evaluationJson.isBlank()) {
            try {
                ev = objectMapper.readValue(evaluationJson, EvaluateInterviewResponse.class);
            } catch (Exception ex) {
                log.warn("Failed to parse evaluation JSON for PDF. sessionId={}", sessionId);
            }
        }

        List<InterviewSessionMessageResponse> allMessages = getSessionMessages(userId, sessionId, null, 100);
        return renderPdf(title, ev, allMessages, mode, style, date);
    }

    // ══════════════════════════════════════════════════════════════
    //  PDF Report Generation — Plain Text Layout
    // ══════════════════════════════════════════════════════════════

    private static final float M = 52;            // page margin
    private static final float PW = PDRectangle.A4.getWidth();   // 595
    private static final float PH = PDRectangle.A4.getHeight();  // 842
    private static final float CW = PW - 2 * M;                  // content width ~491

    // color palette (minimal)
    private static final int C_DARK   = 0x1e293b;
    private static final int C_GRAY   = 0x64748b;
    private static final int C_LIGHT  = 0x94a3b8;
    private static final int C_BLUE   = 0x2563eb;
    private static final int C_GREEN  = 0x059669;
    private static final int C_AMBER  = 0xd97706;
    private static final int C_RED    = 0xdc2626;
    private static final int C_PURPLE = 0x7c3aed;

    private byte[] renderPdf(String title, EvaluateInterviewResponse ev,
                              List<InterviewSessionMessageResponse> messages,
                              String mode, String style, String date) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDType0Font f = loadChineseFont(doc);
            PDType0Font fb = loadChineseBoldFont(doc);
            if (fb == null) fb = f; // 回退到普通字体
            if (f == null) throw new BusinessException(500, "PDF 字体加载失败");

            float y = PH - M;
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            final int[] pageNum = {1};

            // 标题（加粗）
            y = drawCentered(cs, fb, 20, "面试评估报告", y, C_DARK);
            y -= 16;

            // 元信息
            String modeLabel = "ALGORITHM".equalsIgnoreCase(mode) ? "算法面试" : "综合面试";
            String styleLabel = "PRESSURE".equalsIgnoreCase(style) ? "压力式" : "引导式";
            String meta = title + " | " + modeLabel + " · " + styleLabel;
            if (date != null && !date.isBlank()) meta += " | " + date;
            y = drawCentered(cs, f, 9, meta, y, C_GRAY);
            y -= 20;

            // 分数
            if (ev != null && ev.getTotalScore() != null) {
                int sc = ev.getTotalScore();
                String label = scoreLabel(sc);
                y = drawCentered(cs, fb, 18, sc + " / 100  (" + label + ")", y, scoreColor(sc));
                y -= 20;

                // 总结
                String summary = nn(ev.getSummary());
                if (!summary.isBlank()) {
                    for (String line : wrapLines(summary, (int)CW, f, 10f)) {
                        if (y < 60) { cs = newPage(cs, doc, page, pageNum); y = PH - M; }
                        drawTextAbs(cs, f, 10, line, M, y, C_GRAY);
                        y -= 14;
                    }
                }
                y -= 14;

                // 维度
                if (ev.getDimensions() != null && !ev.getDimensions().isEmpty()) {
                    drawTextAbs(cs, f, 10, "各维度得分", M, y, C_DARK);
                    y -= 14;

                    for (EvaluateInterviewResponse.DimensionScore d : ev.getDimensions()) {
                        if (y < 60) { cs = newPage(cs, doc, page, pageNum); y = PH - M; }
                        Integer dmax = d.getMaxScore();
                        String dName = d.getName() != null ? d.getName() : "";
                        int pct = dmax != null && dmax > 0 ? d.getScore() * 100 / dmax : 0;
                        drawTextAbs(cs, f, 10, dName + "  " + d.getScore() + "/" + (dmax != null ? dmax : "—"), M, y, barColor(pct));
                        y -= 12;
                        String cmt = d.getComment() != null ? d.getComment() : "";
                        if (!cmt.isBlank()) {
                            for (String cl : wrapLines(cmt, (int)CW, f, 8f)) {
                                if (y < 60) { cs = newPage(cs, doc, page, pageNum); y = PH - M; }
                                drawTextAbs(cs, f, 8, cl, M, y, C_GRAY);
                                y -= 10;
                            }
                        }
                        y -= 6;
                    }
                    y -= 6;
                }

                // 评估区块
                y = printSection(cs, f, y, doc, page, pageNum, "总体评价", nn(ev.getSummary()));
                y = printSection(cs, f, y, doc, page, pageNum, "亮点", ev.getHighlights());
                y = printSection(cs, f, y, doc, page, pageNum, "改进方向", ev.getImprovements());
                y = printSection(cs, f, y, doc, page, pageNum, "学习建议", ev.getSuggestion());
                y = printSection(cs, f, y, doc, page, pageNum, "简历优化建议", ev.getResumeAdvice());
            }

            // 对话记录
            if (messages != null && !messages.isEmpty()) {
                if (y < 60) { cs = newPage(cs, doc, page, pageNum); y = PH - M; }
                y -= 6;
                drawTextAbs(cs, f, 11, "面试对话记录", M, y, C_DARK);
                y -= 14;

                for (InterviewSessionMessageResponse msg : messages) {
                    if ("system".equalsIgnoreCase(msg.getRole())) continue;
                    String content = msg.getContent();
                    if (content == null || content.isBlank()) continue;

                    boolean isUser = "user".equalsIgnoreCase(msg.getRole());
                    String role = isUser ? "候选人" : "AI 面试官";

                    if (y < 60) { cs = newPage(cs, doc, page, pageNum); y = PH - M; }
                    drawTextAbs(cs, f, 9, role + " · 第" + msg.getTurnNo() + "轮", M, y, isUser ? C_BLUE : C_PURPLE);
                    y -= 12;

                    for (String line : wrapLines(content, (int)CW, f, 9f)) {
                        if (y < 60) { cs = newPage(cs, doc, page, pageNum); y = PH - M; }
                        drawTextAbs(cs, f, 9, line, M, y, C_DARK);
                        y -= 12;
                    }
                    y -= 8;
                }
            }

            // 页码
            drawTextAbs(cs, f, 8, "- " + pageNum[0] + " -", PW / 2 - 12, 30, C_LIGHT);

            cs.close();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("PDF generation failed. title={}", title, e);
            throw new BusinessException(500, "PDF 生成失败");
        }
    }

    private PDPageContentStream newPage(PDPageContentStream cs, PDDocument doc, PDPage page, int[] pageNum) throws IOException {
        cs.close();
        page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        pageNum[0]++;
        return new PDPageContentStream(doc, page);
    }

    private float printSection(PDPageContentStream cs, PDType0Font f, float y,
                               PDDocument doc, PDPage page, int[] pageNum,
                               String title, String content) throws IOException {
        if (content == null || content.isBlank()) return y;
        if (y < 60) { cs = newPage(cs, doc, page, pageNum); y = PH - M; }
        drawTextAbs(cs, f, 10, title, M, y, C_DARK);
        y -= 12;
        for (String line : wrapLines(content, (int)CW, f, 9f)) {
            if (y < 60) { cs = newPage(cs, doc, page, pageNum); y = PH - M; }
            drawTextAbs(cs, f, 9, line, M, y, C_GRAY);
            y -= 12;
        }
        y -= 8;
        return y;
    }

    // ═══════ drawing primitives ═══════

    private float drawTextAbs(PDPageContentStream cs, PDType0Font f, float size,
                              String text, float x, float y, int color) throws IOException {
        cs.beginText();
        cs.setFont(f, size);
        cs.setNonStrokingColor(cf(color >> 16), cf(color >> 8), cf(color));
        cs.newLineAtOffset(x, y);
        cs.showText(text != null ? text : "");
        cs.endText();
        return y; // absolute — caller manages spacing
    }

    private float drawCentered(PDPageContentStream cs, PDType0Font f, float size,
                               String text, float y, int color) throws IOException {
        float tw = stringWidth(f, size, text);
        return drawTextAbs(cs, f, size, text, (PW - tw) / 2, y, color);
    }

    private float stringWidth(PDType0Font f, float size, String text) throws IOException {
        return f.getStringWidth(text != null ? text : "") / 1000f * size;
    }

    // ═══════ page / text helpers ═══════

    private PDPage newPage(PDDocument doc) {
        PDPage p = new PDPage(PDRectangle.A4);
        doc.addPage(p);
        return p;
    }
    private PDPageContentStream newStream(PDDocument doc, PDPage page) throws IOException {
        return new PDPageContentStream(doc, page);
    }

    private List<String> wrapLines(String text, int maxWidthPt, PDType0Font f, float fontSize) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        StringBuilder line = new StringBuilder();
        float currentWidth = 0;
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                result.add(line.toString());
                line.setLength(0);
                currentWidth = 0;
                continue;
            }
            line.append(c);
            try {
                currentWidth += f.getStringWidth(String.valueOf(c)) / 1000f * fontSize;
            } catch (IOException e) {
                currentWidth += fontSize; // fallback
            }
            if (currentWidth >= maxWidthPt) {
                result.add(line.toString());
                line.setLength(0);
                currentWidth = 0;
            }
        }
        if (line.length() > 0) result.add(line.toString());
        return result;
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    private String nn(String s) { return s == null ? "" : s; }

    // ═══════ font loading ═══════

    private PDType0Font loadChineseFont(PDDocument doc) {
        String[] paths = {
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simsun.ttc",
            "/System/Library/Fonts/PingFang.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        };
        for (String p : paths) {
            java.io.File file = new java.io.File(p);
            log.info("Checking font file: {} exists={}", p, file.exists());
            if (file.exists()) {
                try {
                    org.apache.fontbox.ttf.TrueTypeFont ttf = new org.apache.fontbox.ttf.TTFParser().parse(file);
                    PDType0Font font = PDType0Font.load(doc, ttf, false);
                    log.info("Successfully loaded font: {}", p);
                    return font;
                } catch (IOException e) {
                    log.warn("Failed to load font {}: {}", p, e.getMessage());
                }
            }
        }
        return null;
    }

    private PDType0Font loadChineseBoldFont(PDDocument doc) {
        String[] paths = {
            "C:/Windows/Fonts/msyhbd.ttc",
            "C:/Windows/Fonts/simhei.ttf",
            "/System/Library/Fonts/STHeiti Light.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
        };
        for (String p : paths) {
            java.io.File file = new java.io.File(p);
            if (file.exists()) {
                try {
                    org.apache.fontbox.ttf.TrueTypeFont ttf = new org.apache.fontbox.ttf.TTFParser().parse(file);
                    return PDType0Font.load(doc, ttf, false);
                } catch (IOException e) {
                    log.warn("Failed to load bold font {}: {}", p, e.getMessage());
                }
            }
        }
        return null;
    }

    // ═══════ color helpers ═══════

    private static float cf(int c) { return (c & 0xff) / 255.0f; }

    private void setFill(PDPageContentStream cs, int hex) throws IOException {
        cs.setNonStrokingColor(cf(hex >> 16), cf(hex >> 8), cf(hex));
    }
    private void setStroke(PDPageContentStream cs, int hex) throws IOException {
        cs.setStrokingColor(cf(hex >> 16), cf(hex >> 8), cf(hex));
    }

    private int scoreColor(int s) {
        if (s >= 90) return 0x059669;
        if (s >= 75) return 0x2563eb;
        if (s >= 60) return 0xd97706;
        return 0xdc2626;
    }
    private String scoreLabel(int s) {
        if (s >= 90) return "优秀";
        if (s >= 75) return "良好";
        if (s >= 60) return "一般";
        return "需努力";
    }
    private int barColor(int pct) {
        if (pct >= 75) return 0x059669;
        if (pct >= 50) return 0xd97706;
        return 0xdc2626;
    }

    private String normalizeReportMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        String value = mode.trim().toUpperCase(Locale.ROOT);
        if (!"COMPREHENSIVE".equals(value) && !"ALGORITHM".equals(value)) {
            throw new BusinessException("参数错误：mode 仅支持 COMPREHENSIVE/ALGORITHM");
        }
        return value;
    }

    private Integer modeToDifficulty(String mode) {
        if ("ALGORITHM".equals(mode)) {
            return 2;
        }
        if ("COMPREHENSIVE".equals(mode)) {
            return 1;
        }
        return null;
    }

    private String difficultyToModeString(Integer difficulty) {
        if (difficulty == null) {
            return "COMPREHENSIVE";
        }
        return difficulty == 2 ? "ALGORITHM" : "COMPREHENSIVE";
    }

    private String focusToStyleString(Integer focus) {
        if (focus == null) {
            return "GUIDED";
        }
        return focus == 2 ? "PRESSURE" : "GUIDED";
    }

    private Integer extractTotalScore(String evaluationJson) {
        if (evaluationJson == null || evaluationJson.isBlank()) {
            return null;
        }
        try {
            EvaluateInterviewResponse eval = objectMapper.readValue(evaluationJson, EvaluateInterviewResponse.class);
            return eval == null ? null : eval.getTotalScore();
        } catch (Exception ex) {
            return null;
        }
    }

    public SseEmitter streamSession(Long userId, Long sessionId, String streamId, Long messageId) {
        getSessionDataWithPermissionCheck(userId, sessionId);
        String normalizedStreamId = trimToNull(streamId);
        if (normalizedStreamId != null) {
            return streamSingleMessageByStreamId(sessionId, normalizedStreamId);
        }
        Map<String, Object> streamTarget = messageId == null
                ? getLatestStreamMessage(sessionId)
                : getStreamMessageById(sessionId, messageId);
        String content = streamTarget.get("content").toString();
        SseEmitter emitter = new SseEmitter(30000L);
        String systemPrompt = "你是输出通道。请逐字输出用户提供的文本，不要改写，不要添加任何额外说明。";
        String userPrompt = "请逐字输出以下文本：\n" + content;
        AtomicBoolean completed = new AtomicBoolean(false);
        Disposable disposable = deepSeekService.streamRawCompletion(systemPrompt, userPrompt, 0D).subscribe(data -> {
            try {
                if (completed.get()) {
                    return;
                }
                emitter.send(SseEmitter.event().data(data));
                if ("[DONE]".equals(data)) {
                    completed.set(true);
                    emitter.complete();
                }
            } catch (Exception ex) {
                if (completed.compareAndSet(false, true)) {
                    emitter.completeWithError(ex);
                }
            }
        }, ex -> {
            if (completed.compareAndSet(false, true)) {
                emitter.completeWithError(ex);
            }
        }, () -> {
            if (completed.compareAndSet(false, true)) {
                try {
                    emitter.send(SseEmitter.event().data("[DONE]"));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });
        emitter.onCompletion(() -> {
            if (!completed.get()) {
                disposable.dispose();
            }
        });
        emitter.onError(ex -> {
            if (!completed.get()) {
                disposable.dispose();
            }
        });
        emitter.onTimeout(() -> {
            if (!completed.get()) {
                disposable.dispose();
            }
        });
        return emitter;
    }

    private SseEmitter streamSingleMessageByStreamId(Long sessionId, String streamId) {
        Map<String, Object> streamData = getStreamDataById(sessionId, streamId);
        Long targetMessageId = parseLong(streamData.get("targetMessageId"));
        if (targetMessageId == null || targetMessageId <= 0) {
            throw new BusinessException(404, "流目标消息不存在");
        }
        Map<Object, Object> messageData = stringRedisTemplate.opsForHash().entries(buildSessionMessageKey(sessionId, targetMessageId));
        if (messageData == null || messageData.isEmpty()) {
            throw new BusinessException(404, "消息不存在");
        }
        String content = defaultString(asString(messageData.get("content")), "");
        Integer turnNo = parseInteger(messageData.get("turnNo"));
        Boolean finished = parseBoolean(streamData.get("finished"));
        SseEmitter emitter = new SseEmitter(30000L);
        emitter.onTimeout(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(buildErrorEventData(targetMessageId, "MODEL_TIMEOUT", "模型响应超时，已回退到兜底文案")));
            } catch (Exception ignored) {
            }
            emitter.complete();
        });
        try {
            ASYNC_EXECUTOR.execute(() -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message_start")
                            .data(buildMessageStartEventData(targetMessageId, turnNo)));
                    for (String chunk : splitMarkdownContent(content)) {
                        emitter.send(SseEmitter.event()
                                .name("delta")
                                .data(buildDeltaEventData(targetMessageId, chunk)));
                        Thread.sleep(35L);
                    }
                    emitter.send(SseEmitter.event()
                            .name("message_done")
                            .data(buildMessageDoneEventData(targetMessageId)));
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(buildDoneEventData(streamId, Boolean.TRUE.equals(finished))));
                    emitter.complete();
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(buildErrorEventData(targetMessageId, resolveStreamErrorCode(ex), resolveStreamErrorMessage(ex))));
                    } catch (Exception ignored) {
                    }
                    emitter.completeWithError(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            log.warn("SSE fallback rejected (pool full), sessionId={}", sessionId);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(buildErrorEventData(targetMessageId, "SERVER_BUSY", "服务器繁忙，请稍后重试")));
            } catch (Exception ignored) {
            }
            emitter.complete();
        }
        return emitter;
    }

    private String extractDeltaContent(String rawLine) {
        if (rawLine == null || rawLine.isBlank() || "[DONE]".equals(rawLine)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(rawLine, new TypeReference<Map<String, Object>>() {});
            Object choicesObj = map.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                return null;
            }
            Object firstChoice = choices.get(0);
            if (!(firstChoice instanceof Map<?, ?> choice)) {
                return null;
            }
            Object deltaObj = choice.get("delta");
            if (!(deltaObj instanceof Map<?, ?> delta)) {
                return null;
            }
            Object contentObj = delta.get("content");
            if (contentObj != null && !contentObj.toString().isEmpty()) {
                return contentObj.toString();
            }
            // Never fall back to reasoning_content — it contains the model's
            // internal chain-of-thought and would leak into the interview chat.
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildStreamingSystemPrompt(String mode, boolean pressure) {
        boolean isAlgo = "ALGORITHM".equals(mode);
        if (isAlgo) {
            String algoBase = "你是大厂算法面试官，正在进行【算法专项模拟面试】。"
                    + "你的职责是按照预设的算法追问链路（朴素解法→优化思路→复杂度分析→边界Case→变式迁移），"
                    + "逐步引导候选人完成全流程。"
                    + "核心规则：你必须以第一人称直接对候选人说话，就像真实面试官一样。"
                    + "严禁输出你的思考过程、内心独白、分析笔记或对流程的第三方描述。"
                    + "你的输出就是直接说给候选人听的话，不是写给自己看的备忘录。"
                    + "严格要求："
                    + "1) 一次只问一个与当前算法步骤强相关的问题；"
                    + "2) 点评必须聚焦算法正确性、复杂度分析、边界处理；"
                    + "3) 追问必须严格按照当前算法步骤的定义展开，不可跳步或偏题；"
                    + "4) 不输出列表、不输出开场白；"
                    + "5) 输出中文。"
                    + "你必须直接输出 Markdown 格式的完整点评和下一问，不要用 JSON 包裹。"
                    + "格式要求："
                    + "1)【点评结论】说明候选人算法思路的正确性、证据与缺口；"
                    + "2)【参考答案】给出当前算法题的标准解法（含关键步骤、时间/空间复杂度、边界case）；"
                    + "3)【改进建议】给候选人可直接执行的算法改进方向；"
                    + "4)【下一问】严格按照算法链路输出下一个步骤的问题；"
                    + "重要：如果当前已是算法追问链路的最后一步，必须将【下一问】替换为【面试总结】，"
                    + "总结候选人算法能力的整体表现、薄弱环节和后续练习建议，严禁再提新问题。";
            if (pressure) {
                return algoBase
                        + "当前为压力面：点评必须直指算法错误，不允许模糊表扬；"
                        + "追问必须更尖锐，要求候选人当场给出最优复杂度证明或反例。";
            }
            return algoBase
                    + "当前为引导面：点评重在帮助候选人补齐算法细节，"
                    + "追问时给出一到两个提示方向。";
        }

        // COMPREHENSIVE mode
        String compBase = "你是大厂技术面试官，正在进行【综合能力模拟面试】。"
                + "你的职责是围绕岗位核心能力（项目经验、技术深度、系统设计、业务理解、软技能），"
                + "按轮次逐步提高问题深度，从广度和深度两个维度验证候选人。"
                + "核心规则：你必须以第一人称直接对候选人说话，就像真实面试官一样。"
                + "严禁输出你的思考过程、内心独白、分析笔记或对流程的第三方描述。"
                + "你的输出就是直接说给候选人听的话，不是写给自己看的备忘录。"
                + "严格要求："
                + "1) 一次只问一个问题；"
                + "2) 问题必须与岗位JD和候选人简历强相关，不可泛泛而谈；"
                + "3) 追问必须基于候选人上一轮的回答自然延伸，不可凭空切换话题；"
                + "4) 综合面试不涉及纯算法题，聚焦工程能力、架构决策、项目落地和团队协作；"
                + "5) 不输出列表、不输出开场白；"
                + "6) 输出中文。"
                + "本面试共6轮，第1轮是开场自我介绍和项目经历简述。"
                + "你必须直接输出 Markdown 格式的完整点评和下一问，不要用 JSON 包裹。"
                + "格式要求："
                + "1)【点评结论】说明候选人本轮回答的证据与缺口，对照岗位JD给出判断；"
                + "2)【参考答案】给出本问题的标准作答思路或业内最佳实践（可执行、可验证）；"
                + "3)【改进建议】给候选人可直接执行的改进动作（具体到技术选型、指标或方案）；"
                + "4)【下一问】一个新的、与上一问强相关的追问，继续深挖综合能力；"
                + "重要：如果是第6轮（最后一轮），必须将【下一问】替换为【面试总结】，"
                + "总结候选人整体表现、核心优势与待提升方向，并给出后续学习建议，严禁再提新问题。";
        if (pressure) {
            return compBase
                    + "当前为压力面：点评必须直指问题，不允许模糊表扬；追问必须更尖锐，"
                    + "强制要求候选人给出可验证依据（数字、阈值、日志、线上故障细节、回滚方案）。";
        }
        return compBase
                + "当前为引导面：点评保持专业客观，追问重在帮助候选人补齐关键细节。";
    }

    private String buildStreamingUserPrompt(String jobTitle, String mode, String style,
                                            Integer turnNo, Integer algoStep, int totalAlgoSteps,
                                            String stepsText, String jdText, String resumeContext,
                                            String answerContent, String conversationHistory) {
        boolean isAlgo = "ALGORITHM".equals(mode);
        StringBuilder sb = new StringBuilder();
        sb.append("岗位：").append(safe(jobTitle))
                .append("\n模式：").append(safe(mode))
                .append("\n风格：").append(safe(style))
                .append("\n当前轮次：").append(turnNo);
        if (isAlgo) {
            sb.append("\n算法步骤：").append(algoStep).append("/").append(totalAlgoSteps)
                    .append("\n算法步骤定义：").append(stepsText);
        } else {
            sb.append("\n本面试共6轮，当前是第").append(turnNo).append("轮。");
            if (turnNo >= 6) {
                sb.append("这是最后一轮，本轮不需再提下一问，请输出【面试总结】代替【下一问】。");
            }
        }
        sb.append("\nJD：").append(safe(jdText))
                .append("\n简历摘要：").append(safe(resumeContext))
                .append("\n对话历史（用于理解上下文）：\n").append(safe(conversationHistory))
                .append("\n候选人本轮回答：").append(safe(answerContent));
        if (isAlgo) {
            sb.append("\n请按格式输出完整的点评、参考答案、改进建议和下一问。"
                    + "\n注意：下一问必须严格对应算法步骤 ").append(algoStep).append(" 的定义。");
            if ("PRESSURE".equalsIgnoreCase(style)) {
                sb.append("\n当前为压力面：点评语气要尖锐直接，不允许模糊表扬，追问必须具有挑战性。");
            } else {
                sb.append("\n当前为引导面：点评语气专业温和，追问重在循序渐进帮助候选人补齐不足。");
            }
            sb.append("\n注意：不要用 JSON 格式，直接输出 Markdown 内容。");
        } else {
            sb.append("\n请按格式输出完整的点评、参考答案、改进建议和下一问。"
                    + "\n注意：下一问必须是综合能力方向的追问，不要切换到纯算法题。");
            if ("PRESSURE".equalsIgnoreCase(style)) {
                sb.append("\n当前为压力面：点评语气要尖锐直接，不允许模糊表扬，追问必须具有挑战性。");
            } else {
                sb.append("\n当前为引导面：点评语气专业温和，追问重在循序渐进帮助候选人补齐不足。");
            }
            sb.append("\n注意：不要用 JSON 格式，直接输出 Markdown 内容。");
        }
        return sb.toString();
    }

    private boolean isSessionFinished(Long sessionId) {
        Object statusObj = stringRedisTemplate.opsForHash().get(SESSION_KEY_PREFIX + sessionId, "status");
        return "FINISHED".equals(asString(statusObj));
    }

    private void finishStreamedMessage(SseEmitter emitter, Long sessionId, Map<Object, Object> sessionData,
                                       String fullContent, Integer nextTurnNo, Integer nextAlgoStep,
                                       boolean isAlgoMode, List<String> algoFlowSteps, String nowText,
                                       String mode, Integer currentTurnNo, String answerContent) {
        String content = trimToNull(fullContent);
        boolean isFinished;
        if (isAlgoMode) {
            int total = algoFlowSteps.isEmpty() ? 5 : algoFlowSteps.size();
            isFinished = nextAlgoStep > total;
            if (nextAlgoStep > total) {
                nextAlgoStep = total;
            }
        } else {
            isFinished = currentTurnNo >= 6;
        }
        String reviewType = content == null ? decideReviewType(answerContent) : "NEUTRAL";
        if (content == null || content.length() < 20) {
            content = buildAssistantMarkdownContent(
                    buildReviewContent(mode, asString(sessionData.get("style")), currentTurnNo, answerContent, reviewType),
                    buildFollowupContent(mode, asString(sessionData.get("style")), nextTurnNo, nextAlgoStep, isFinished, algoFlowSteps),
                    isFinished);
        }
        if (isFinished && content != null) {
            // 防御性清理：如果 AI 仍然输出了下一问，截断并替换为结束语
            content = content.replaceAll("\\n*【下一问】[\\s\\S]*$", "");
            if (!content.contains("面试已完成") && !content.contains("面试总结")) {
                content = content + "\n\n本场模拟面试已完成。";
            }
        }
        Long assistantMessageId = nextMessageId();
        persistMessage(sessionId, assistantMessageId, "assistant", "ASSISTANT", nextTurnNo, content,
                null, reviewType, false, nowText);
        if (!isSessionFinished(sessionId)) {
            updateSessionProgress(sessionId, nextTurnNo, nextAlgoStep, isFinished, nowText, mode);
            if (isFinished) {
                syncFinishedSessionToDatabase(sessionId, sessionData, nowText);
            }
        }
        deleteTurnLock(sessionId, currentTurnNo);
        try {
            Map<String, Object> doneData = new HashMap<>();
            doneData.put("messageId", assistantMessageId);
            doneData.put("turnNo", nextTurnNo);
            doneData.put("content", content);
            doneData.put("isFinished", isFinished);
            doneData.put("reviewType", reviewType);
            if (isAlgoMode) {
                Map<String, Object> flow = new HashMap<>();
                flow.put("enabled", true);
                flow.put("steps", algoFlowSteps);
                flow.put("currentStep", nextAlgoStep);
                doneData.put("algoFlow", flow);
            }
            emitter.send(SseEmitter.event().name("done").data(doneData));
        } catch (Exception ex) {
            log.warn("Failed to send done SSE event. sessionId={}", sessionId, ex);
        }
        emitter.complete();
    }

    private void handleStreamError(SseEmitter emitter, Long sessionId, Map<Object, Object> sessionData,
                                   String partialContent, Integer currentTurnNo, Integer algoStep,
                                   String mode, List<String> algoFlowSteps, String nowText,
                                   String answerContent, Throwable error) {
        log.warn("DeepSeek streaming failed, falling back to template. sessionId={}, turnNo={}, errorClass={}, message={}",
                sessionId, currentTurnNo, error.getClass().getName(), error.getMessage(), error);
        String reviewType = decideReviewType(answerContent);
        Integer nextTurnNo = currentTurnNo + 1;
        String content;
        if (trimToNull(partialContent) != null) {
            content = partialContent;
        } else {
            String errorHint = extractDeepSeekErrorHint(error);
            content = buildAssistantMarkdownContent(
                    buildReviewContent(mode, asString(sessionData.get("style")), currentTurnNo, answerContent, reviewType),
                    buildFollowupContent(mode, asString(sessionData.get("style")), nextTurnNo, algoStep, false, algoFlowSteps),
                    false);
            if (errorHint != null) {
                content = "> 提示：" + errorHint + "\n\n" + content;
            }
        }
        boolean isAlgoMode = "ALGORITHM".equals(mode);
        Long assistantMessageId = nextMessageId();
        persistMessage(sessionId, assistantMessageId, "assistant", "ASSISTANT", nextTurnNo, content,
                null, reviewType, false, nowText);
        if (!isSessionFinished(sessionId)) {
            updateSessionProgress(sessionId, nextTurnNo, algoStep, false, nowText, mode);
        }
        deleteTurnLock(sessionId, currentTurnNo);
        try {
            Map<String, Object> doneData = new HashMap<>();
            doneData.put("messageId", assistantMessageId);
            doneData.put("turnNo", nextTurnNo);
            doneData.put("content", content);
            doneData.put("isFinished", false);
            doneData.put("reviewType", reviewType);
            if (isAlgoMode) {
                Map<String, Object> flow = new HashMap<>();
                flow.put("enabled", true);
                flow.put("steps", algoFlowSteps);
                flow.put("currentStep", algoStep);
                doneData.put("algoFlow", flow);
            }
            emitter.send(SseEmitter.event().name("done").data(doneData));
        } catch (Exception ex) {
            log.warn("Failed to send error done SSE event. sessionId={}", sessionId, ex);
        }
        emitter.complete();
    }

    private void handleStreamTimeout(SseEmitter emitter, Long sessionId, Map<Object, Object> sessionData,
                                     String partialContent, Integer currentTurnNo, Integer algoStep,
                                     String mode, List<String> algoFlowSteps, String nowText,
                                     String answerContent) {
        log.warn("DeepSeek streaming timeout. sessionId={}, turnNo={}", sessionId, currentTurnNo);
        String reviewType = decideReviewType(answerContent);
        Integer nextTurnNo = currentTurnNo + 1;
        String content;
        if (trimToNull(partialContent) != null) {
            content = partialContent + "\n\n（AI 输出中断，以上为部分内容）";
        } else {
            content = buildAssistantMarkdownContent(
                    buildReviewContent(mode, asString(sessionData.get("style")), currentTurnNo, answerContent, reviewType),
                    buildFollowupContent(mode, asString(sessionData.get("style")), nextTurnNo, algoStep, false, algoFlowSteps),
                    false);
        }
        boolean isAlgoMode = "ALGORITHM".equals(mode);
        Long assistantMessageId = nextMessageId();
        persistMessage(sessionId, assistantMessageId, "assistant", "ASSISTANT", nextTurnNo, content,
                null, reviewType, false, nowText);
        if (!isSessionFinished(sessionId)) {
            updateSessionProgress(sessionId, nextTurnNo, algoStep, false, nowText, mode);
        }
        deleteTurnLock(sessionId, currentTurnNo);
        try {
            Map<String, Object> doneData = new HashMap<>();
            doneData.put("messageId", assistantMessageId);
            doneData.put("turnNo", nextTurnNo);
            doneData.put("content", content);
            doneData.put("isFinished", false);
            doneData.put("reviewType", reviewType);
            if (isAlgoMode) {
                Map<String, Object> flow = new HashMap<>();
                flow.put("enabled", true);
                flow.put("steps", algoFlowSteps);
                flow.put("currentStep", algoStep);
                doneData.put("algoFlow", flow);
            }
            emitter.send(SseEmitter.event().name("done").data(doneData));
        } catch (Exception ignored) {
        }
        emitter.complete();
    }

    private String extractDeepSeekErrorHint(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        if (message == null) {
            return null;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("401") || lower.contains("unauthorized")) {
            return "DeepSeek API Key 无效或已过期，已使用模板点评代替。请检查配置项 deepseek.api-key。";
        }
        if (lower.contains("403") || lower.contains("forbidden")) {
            return "DeepSeek API 无访问权限，已使用模板点评代替。请检查 API Key 权限。";
        }
        if (lower.contains("429") || lower.contains("rate limit")) {
            return "DeepSeek API 请求频率超限，已使用模板点评代替。请稍后重试。";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "DeepSeek API 响应超时，已使用模板点评代替。请检查网络连接。";
        }
        if (lower.contains("connect") || lower.contains("refused") || lower.contains("unreachable")) {
            return "无法连接 DeepSeek 服务器，已使用模板点评代替。请检查网络连接。";
        }
        log.warn("DeepSeek API error detail: {}", message);
        return "AI 服务暂不可用，已使用模板点评代替。";
    }

    private String buildConversationHistory(Long userId, Long sessionId) {
        List<InterviewSessionMessageResponse> allMessages = getSessionMessages(userId, sessionId, null, 100);
        StringBuilder sb = new StringBuilder();
        for (InterviewSessionMessageResponse msg : allMessages) {
            String role = msg.getRole();
            if ("system".equals(role)) {
                continue;
            }
            String label = "user".equals(role) ? "候选人" : "面试官";
            sb.append("[").append(label).append(" 第").append(msg.getTurnNo()).append("轮] ")
                    .append(defaultString(msg.getContent(), "")).append("\n");
        }
        String history = sb.toString().trim();
        return history.isEmpty() ? "（尚无历史对话）" : history;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private void persistSession(Long userId, Long sessionId, String sessionCode, String title, String mode, String style,
                                String welcomeText, String jdText, String resumeContext, String algorithmDirection,
                                List<String> algoFlowSteps, LocalDateTime now) {
        String key = SESSION_KEY_PREFIX + sessionId;
        Map<String, String> payload = new HashMap<>();
        payload.put("id", String.valueOf(sessionId));
        payload.put("userId", String.valueOf(userId));
        payload.put("sessionCode", sessionCode);
        payload.put("title", title);
        payload.put("mode", mode);
        payload.put("style", style);
        payload.put("status", "IN_PROGRESS");
        payload.put("welcomeText", welcomeText);
        payload.put("resumeContext", resumeContext);
        payload.put("jdText", trimToNull(jdText));
        if ("ALGORITHM".equals(mode)) {
            payload.put("algorithmDirection", defaultString(algorithmDirection, ""));
            payload.put("algoFlowSteps", serializeAlgoFlowSteps(algoFlowSteps));
            payload.put("algoCurrentStep", "1");
        }
        payload.put("startedAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("updatedAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        stringRedisTemplate.opsForHash().putAll(key, payload);
        stringRedisTemplate.expire(key, SESSION_TTL);

        String dbStartedAt = toDatabaseDateTime(now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), null);
        try {
            interviewSessionMapper.insertSession(
                    sessionId,
                    userId,
                    title,
                    "ALGORITHM".equals(mode) ? 2 : 1,
                    "ALGORITHM".equals(mode) ? 2 : 1,
                    "PRESSURE".equals(style) ? 2 : 1,
                    dbStartedAt
            );
        } catch (Exception ex) {
            log.error("Failed to persist session to MySQL, sessionId={}, reason={}", sessionId, ex.getMessage());
        }
    }

    private void persistFirstMessage(Long sessionId, Long messageId, String content, String createdAt) {
        String key = buildSessionMessageKey(sessionId, messageId);
        Map<String, String> payload = new HashMap<>();
        payload.put("id", String.valueOf(messageId));
        payload.put("sessionId", String.valueOf(sessionId));
        payload.put("role", "assistant");
        payload.put("kind", "QUESTION");
        payload.put("turnNo", "1");
        payload.put("content", content);
        payload.put("contentFormat", "MARKDOWN");
        payload.put("typing", "true");
        payload.put("createdAt", createdAt);
        stringRedisTemplate.opsForHash().putAll(key, payload);
        stringRedisTemplate.expire(key, SESSION_TTL);
        String messagesKey = buildSessionMessagesKey(sessionId);
        stringRedisTemplate.opsForList().rightPush(messagesKey, String.valueOf(messageId));
        stringRedisTemplate.expire(messagesKey, SESSION_TTL);

        try {
            interviewMessageMapper.insertMessage(messageId, sessionId, "assistant", 1, content,
                    toDatabaseDateTime(createdAt, null));
        } catch (Exception ex) {
            log.error("Failed to persist message to MySQL, messageId={}, sessionId={}, reason={}",
                    messageId, sessionId, ex.getMessage());
        }
    }

    private void deleteTurnLock(Long sessionId, Integer turnNo) {
        String key = SESSION_KEY_PREFIX + sessionId + ":turn-lock:" + turnNo;
        stringRedisTemplate.delete(key);
    }

    private void persistMessage(Long sessionId, Long messageId, String role, String kind, Integer turnNo,
                                String content, String reviewTitle, String reviewType, Boolean typing, String createdAt) {
        String key = buildSessionMessageKey(sessionId, messageId);
        Map<String, String> payload = new HashMap<>();
        payload.put("id", String.valueOf(messageId));
        payload.put("sessionId", String.valueOf(sessionId));
        payload.put("role", role);
        payload.put("kind", kind);
        payload.put("turnNo", String.valueOf(turnNo));
        payload.put("content", content);
        payload.put("contentFormat", "MARKDOWN");
        if (reviewTitle != null) {
            payload.put("reviewTitle", reviewTitle);
        }
        if (reviewType != null) {
            payload.put("reviewType", reviewType);
        }
        payload.put("typing", String.valueOf(Boolean.TRUE.equals(typing)));
        payload.put("createdAt", createdAt);

        String messagesKey = buildSessionMessagesKey(sessionId);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] messagesKeyBytes = messagesKey.getBytes(StandardCharsets.UTF_8);
        byte[] msgIdBytes = String.valueOf(messageId).getBytes(StandardCharsets.UTF_8);
        Map<byte[], byte[]> hashPayload = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            hashPayload.put(
                    entry.getKey().getBytes(StandardCharsets.UTF_8),
                    entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        long ttlSeconds = SESSION_TTL.getSeconds();
        stringRedisTemplate.executePipelined((RedisConnection connection) -> {
            connection.hMSet(keyBytes, hashPayload);
            connection.expire(keyBytes, ttlSeconds);
            connection.rPush(messagesKeyBytes, msgIdBytes);
            connection.expire(messagesKeyBytes, ttlSeconds);
            return null;
        });

        try {
            interviewMessageMapper.insertMessage(messageId, sessionId, role, turnNo, content,
                    toDatabaseDateTime(createdAt, null));
        } catch (Exception ex) {
            log.error("Failed to persist message to MySQL, messageId={}, sessionId={}, reason={}",
                    messageId, sessionId, ex.getMessage());
        }
    }

    private CreateInterviewSessionResponse.FirstMessage buildFirstMessage(Long messageId, String content, String createdAt) {
        CreateInterviewSessionResponse.FirstMessage firstMessage = new CreateInterviewSessionResponse.FirstMessage();
        firstMessage.setMessageId(messageId);
        firstMessage.setRole("assistant");
        firstMessage.setKind("QUESTION");
        firstMessage.setTurnNo(1);
        firstMessage.setContent(content);
        firstMessage.setCreatedAt(createdAt);
        return firstMessage;
    }

    private CreateInterviewSessionResponse.AlgoFlow buildAlgoFlow(String mode, List<String> steps) {
        if (!"ALGORITHM".equals(mode)) {
            return null;
        }
        CreateInterviewSessionResponse.AlgoFlow flow = new CreateInterviewSessionResponse.AlgoFlow();
        flow.setEnabled(true);
        flow.setSteps(steps);
        flow.setCurrentStep(1);
        return flow;
    }

    private InterviewSessionDetailResponse.AlgoFlow buildSessionDetailAlgoFlow(String mode, Object currentStepValue, Object stepsValue) {
        if (!"ALGORITHM".equals(mode)) {
            return null;
        }
        InterviewSessionDetailResponse.AlgoFlow flow = new InterviewSessionDetailResponse.AlgoFlow();
        flow.setEnabled(true);
        flow.setSteps(parseAlgoFlowSteps(stepsValue));
        Integer currentStep = parseInteger(currentStepValue);
        flow.setCurrentStep(currentStep == null || currentStep < 1 ? 1 : currentStep);
        return flow;
    }

    private String resolveResumeContext(CreateInterviewSessionRequest request) {
        String extractedResumeText = trimToNull(request.getExtractedResumeText());
        String resumeText = trimToNull(request.getResumeText());
        String baseContext;
        if (extractedResumeText != null) {
            baseContext = extractedResumeText;
        } else {
            String resumeFileId = trimToNull(request.getResumeFileId());
            if (resumeFileId == null) {
                throw new BusinessException("请上传简历文件");
            }
            Object text = stringRedisTemplate.opsForHash().get(RESUME_FILE_KEY_PREFIX + resumeFileId, "extractedText");
            if (text == null) {
                throw new BusinessException("简历文件不存在或已过期");
            }
            baseContext = text.toString().trim();
        }
        if (resumeText == null) {
            return baseContext;
        }
        if (baseContext == null || baseContext.isBlank()) {
            return resumeText;
        }
        return baseContext + "\n" + resumeText;
    }

    private String normalizeMode(String mode) {
        String value = trimToNull(mode);
        if (value == null) {
            throw new BusinessException("参数错误：mode 不能为空");
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!"COMPREHENSIVE".equals(upper) && !"ALGORITHM".equals(upper)) {
            throw new BusinessException("参数错误：mode 仅支持 COMPREHENSIVE/ALGORITHM");
        }
        return upper;
    }

    private String normalizeStyle(String style) {
        String value = trimToNull(style);
        if (value == null) {
            throw new BusinessException("参数错误：style 不能为空");
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!"GUIDED".equals(upper) && !"PRESSURE".equals(upper)) {
            throw new BusinessException("参数错误：style 仅支持 GUIDED/PRESSURE");
        }
        return upper;
    }

    private String resolveJobTitle(String mode, String jobTitle, String algorithmDirection) {
        String normalized = trimToNull(jobTitle);
        if (normalized != null) {
            return normalized;
        }
        if ("ALGORITHM".equals(mode)) {
            String direction = defaultString(trimToNull(algorithmDirection), "通用算法");
            return direction + "专项面试";
        }
        throw new BusinessException("请填写意向岗位");
    }

    private String resolveJdText(String mode, String jdText, String algorithmDirection) {
        String normalized = trimToNull(jdText);
        if (normalized != null) {
            return normalized;
        }
        if ("ALGORITHM".equals(mode)) {
            String direction = defaultString(trimToNull(algorithmDirection), "通用算法");
            return "重点考察" + direction + "方向的题目建模、复杂度分析、边界处理与变式迁移能力。";
        }
        throw new BusinessException("请填写岗位要求");
    }

    private List<String> resolveAlgoFlowSteps(String mode, String style, String algorithmDirection) {
        if (!"ALGORITHM".equals(mode)) {
            return List.of();
        }
        String direction = trimToNull(algorithmDirection);
        if (direction == null) {
            throw new BusinessException("参数错误：algorithmDirection 不能为空");
        }
        try {
            List<String> steps = deepSeekService.generateAlgorithmFlowSteps(direction, style);
            if (steps != null && !steps.isEmpty()) {
                return steps;
            }
        } catch (Exception ignored) {
        }
        return buildLocalAlgoFlowSteps(direction);
    }

    private List<String> buildLocalAlgoFlowSteps(String algorithmDirection) {
        List<String> directions = parseAlgorithmDirections(algorithmDirection);
        String topic = directions.isEmpty() ? "算法基础" : directions.get(0);
        String allTopics = directions.isEmpty() ? "算法基础" : String.join("、", directions);
        return List.of(
                "请先确认你在" + allTopics + "上的做题策略与常用解题框架",
                "请围绕" + topic + "给出一题朴素解法并说明关键思路",
                "请继续优化该解法并解释时间复杂度与空间复杂度",
                "请补充边界输入、异常场景与可验证测试用例",
                "请给出" + topic + "同类变式题的迁移方法与取舍依据"
        );
    }

    private List<String> parseAlgorithmDirections(String raw) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            return List.of();
        }
        String[] parts = normalized.split("[，,、/\\s]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String value = trimToNull(part);
            if (value == null || result.contains(value)) {
                continue;
            }
            result.add(value);
            if (result.size() >= 6) {
                break;
            }
        }
        return result;
    }

    private String buildWelcomeText(String mode, String style) {
        String modeText = "ALGORITHM".equals(mode) ? "算法专项模拟面试" : "综合能力模拟面试";
        String styleText = "PRESSURE".equals(style) ? "压力面试风格" : "循序渐进风格";
        return "已进入" + modeText + "，本场采用" + styleText + "。";
    }

    private String buildFirstQuestion(String mode, String jobTitle, String style, List<String> algoFlowSteps) {
        if ("ALGORITHM".equals(mode)) {
            String firstStep = algoFlowSteps.isEmpty() ? "请先介绍你最熟悉的一类算法题，并说明朴素解法。" : algoFlowSteps.get(0);
            if ("PRESSURE".equals(style)) {
                return "我们直接开始算法题。第 1 步：" + firstStep + "。请给出可验证的结论。";
            }
            return "我们先从算法热身开始。第 1 步：" + firstStep;
        }
        if ("PRESSURE".equals(style)) {
            return "你正在应聘" + jobTitle + "。请先做 60 秒自我介绍，并说明你最能证明胜任该岗位的项目成果。";
        }
        return "欢迎参加" + jobTitle + "模拟面试。先请你做一个简短自我介绍，并谈谈与你岗位最匹配的经历。";
    }

    private String buildSessionCode(LocalDateTime now, Long sessionId) {
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long suffix = sessionId % 1000;
        return "IM-" + date + "-" + String.format("%03d", suffix);
    }

    private Map<Object, Object> getSessionDataWithPermissionCheck(Long userId, Long sessionId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException("参数错误：sessionId 非法");
        }
        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        Map<Object, Object> sessionData = stringRedisTemplate.opsForHash().entries(sessionKey);
        if (sessionData == null || sessionData.isEmpty()) {
            // Redis miss — fall back to MySQL
            Map<String, Object> dbRow = interviewSessionMapper.selectSessionById(sessionId);
            if (dbRow == null || dbRow.isEmpty()) {
                throw new BusinessException(404, "会话不存在");
            }
            Long dbOwnerId = ServiceUtils.toLongOrNull(dbRow.get("user_id"));
            if (dbOwnerId == null || !dbOwnerId.equals(userId)) {
                throw new BusinessException(403, "无权限访问该会话");
            }
            // Rebuild session data in Redis from MySQL row
            Integer dbDifficulty = ServiceUtils.toIntegerOrNull(dbRow.get("difficulty"));
            Integer dbStyleInt = ServiceUtils.toIntegerOrNull(dbRow.get("style"));
            String mode = (dbDifficulty != null && dbDifficulty == 2) ? "ALGORITHM" : "COMPREHENSIVE";
            String style = (dbStyleInt != null && dbStyleInt == 2) ? "PRESSURE" : "GUIDED";
            Map<String, String> rebuilt = new HashMap<>();
            rebuilt.put("id", String.valueOf(sessionId));
            rebuilt.put("userId", String.valueOf(userId));
            rebuilt.put("title", defaultString(asString(dbRow.get("title")), ""));
            rebuilt.put("mode", mode);
            rebuilt.put("style", style);
            rebuilt.put("status", "1".equals(String.valueOf(dbRow.get("status"))) ? "FINISHED" : "IN_PROGRESS");
            String startedAt = dbRow.get("started_at") != null ? String.valueOf(dbRow.get("started_at")) : "";
            rebuilt.put("startedAt", startedAt);
            String finishedAt = dbRow.get("finished_at") != null ? String.valueOf(dbRow.get("finished_at")) : "";
            rebuilt.put("updatedAt", finishedAt.isBlank() ? startedAt : finishedAt);
            rebuilt.put("sessionCode", "");
            rebuilt.put("welcomeText", "");
            rebuilt.put("resumeContext", "");
            rebuilt.put("jdText", "");
            if ("ALGORITHM".equals(mode)) {
                rebuilt.put("algorithmDirection", "");
                rebuilt.put("algoFlowSteps", "");
                rebuilt.put("algoCurrentStep", "1");
            }
            stringRedisTemplate.opsForHash().putAll(sessionKey, rebuilt);
            stringRedisTemplate.expire(sessionKey, SESSION_TTL);
            sessionData = new HashMap<>(rebuilt);
        } else {
            // Refresh TTL on access
            stringRedisTemplate.expire(sessionKey, SESSION_TTL);
            Long sessionOwnerId = parseLong(sessionData.get("userId"));
            if (sessionOwnerId == null || !sessionOwnerId.equals(userId)) {
                throw new BusinessException(403, "无权限访问该会话");
            }
        }
        return sessionData;
    }

    private InterviewSessionMessageResponse buildSessionMessageResponse(Map<Object, Object> messageData, Long fallbackMessageId) {
        InterviewSessionMessageResponse response = new InterviewSessionMessageResponse();
        Long messageId = parseLong(messageData.get("id"));
        response.setId(messageId == null ? fallbackMessageId : messageId);
        response.setRole(asString(messageData.get("role")));
        response.setKind(asString(messageData.get("kind")));
        response.setTurnNo(parseInteger(messageData.get("turnNo")));
        response.setContent(asString(messageData.get("content")));
        response.setContentFormat(defaultString(asString(messageData.get("contentFormat")), "MARKDOWN"));
        response.setReviewTitle(asString(messageData.get("reviewTitle")));
        response.setReviewType(asString(messageData.get("reviewType")));
        Boolean typing = parseBoolean(messageData.get("typing"));
        response.setTyping(typing == null ? Boolean.FALSE : typing);
        response.setCreatedAt(asString(messageData.get("createdAt")));
        return response;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 30;
        }
        if (limit <= 0 || limit > 100) {
            throw new BusinessException("参数错误：limit 取值范围为 1-100");
        }
        return limit;
    }

    private String buildSessionMessagesKey(Long sessionId) {
        return SESSION_KEY_PREFIX + sessionId + SESSION_MESSAGES_KEY_SUFFIX;
    }

    private String buildSessionMessageKey(Long sessionId, Long messageId) {
        return SESSION_KEY_PREFIX + sessionId + ":message:" + messageId;
    }


    private Long nextMessageId() {
        Long messageId = stringRedisTemplate.opsForValue().increment(MESSAGE_ID_KEY);
        if (messageId == null) {
            throw new BusinessException(500, "服务器内部错误");
        }
        return messageId;
    }

    private String decideReviewType(String answerContent) {
        int score = calculateAnswerQuality(answerContent);
        if (score >= 70) {
            return "POSITIVE";
        }
        if (score >= 35) {
            return "NEUTRAL";
        }
        return "IMPROVE";
    }

    private int calculateAnswerQuality(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int score = 0;

        // ── 维度1: 内容丰富度 (0-40) ──
        int length = text.length();
        if (length >= 200) {
            score += 40;
        } else if (length >= 100) {
            score += 30;
        } else if (length >= 50) {
            score += 20;
        } else if (length >= 20) {
            score += 10;
        }

        // ── 维度2: 结构完整性 (0-30) ──
        boolean hasBulletPoints = text.matches("(?s).*([\\d]+\\.\\s|[-*•]\\s|（[\\d一二三四五六七八九十]+）).*");
        if (hasBulletPoints) {
            score += 15;
        }
        boolean hasParagraphs = text.contains("\n\n") || text.contains("\r\n\r\n");
        if (hasParagraphs) {
            score += 15;
        }

        // ── 维度3: 技术深度 (0-30) ──
        int techScore = 0;
        boolean hasTechTerms = text.matches("(?s).*(框架|架构|算法|设计模式|数据结构|分布式|并发|缓存|数据库|索引|优化|性能" +
                "|API|接口|微服务|容器|部署|测试|安全|认证|协议|OAuth|JWT|REST|GraphQL" +
                "|Spring|Java|Python|Go|Rust|JavaScript|TypeScript|Kubernetes|Docker|Redis|Kafka" +
                "|时间复杂度|空间复杂度|O\\(|SQL|NoSQL|MVC|MVVM|DDD|TDD|CI/CD|负载均衡).*");
        if (hasTechTerms) {
            techScore += 10;
        }
        boolean hasQuantified = text.matches("(?s).*\\d+[%％].*") || text.matches("(?s).*\\d+\\s*(ms|秒|分钟|小时|MB|GB|KB).*");
        if (hasQuantified) {
            techScore += 10;
        }
        boolean hasComparison = text.matches("(?s).*(相比|对比|优于|不如|高于|低于|但是|然而|不过|虽然|另一方面" +
                "|however|but|versus|vs\\.|compared|trade.?off|pros?\\s*(/|and)\\s*cons?).*");
        if (hasComparison) {
            techScore += 10;
        }
        score += Math.min(techScore, 30);

        return Math.min(score, 100);
    }

    private String buildReviewContent(String mode, String style, Integer turnNo, String answerContent, String reviewType) {
        String direction;
        if ("POSITIVE".equals(reviewType)) {
            direction = "结构较完整，信息覆盖较好";
        } else if ("NEUTRAL".equals(reviewType)) {
            direction = "主线清晰，但细节还可以再展开";
        } else {
            direction = "核心点偏少，建议补充关键细节";
        }
        String modeText = "ALGORITHM".equals(mode) ? "算法表达" : "岗位匹配度";
        String styleText = "PRESSURE".equals(style) ? "请在压力下保持结论先行" : "建议先结论后展开";
        return "第 " + turnNo + " 轮点评：" + direction + "。当前重点关注" + modeText + "，" + styleText + "。你本轮回答长度约 "
                + answerContent.length() + " 字。";
    }

    private String buildFollowupContent(String mode, String style, Integer nextTurnNo, Integer algoStep, boolean isFinished,
                                        List<String> algoFlowSteps) {
        if (isFinished) {
            return "本场模拟面试已完成。建议你基于本次问答回顾关键失分点，并整理成可复用的答题模板。";
        }
        if ("ALGORITHM".equals(mode)) {
            String stepText = buildAlgorithmStepText(algoStep, algoFlowSteps);
            if ("PRESSURE".equals(style)) {
                return "第 " + nextTurnNo + " 轮追问：" + stepText + "，请直接给出结论并说明取舍理由。";
            }
            return "第 " + nextTurnNo + " 轮追问：" + stepText + "，请按步骤详细说明你的思路。";
        }
        if ("PRESSURE".equals(style)) {
            return "第 " + nextTurnNo + " 轮问题：请用可量化结果说明你在项目中的实际贡献，并回答潜在风险与兜底方案。";
        }
        return "第 " + nextTurnNo + " 轮问题：请补充一个你主导的项目案例，重点说明决策过程、技术取舍和最终结果。";
    }

    private String buildAlgorithmStepText(Integer algoStep, List<String> algoFlowSteps) {
        if (algoStep == null || algoStep < 1 || algoFlowSteps == null || algoFlowSteps.isEmpty()) {
            return "请继续围绕当前算法方向展开推导";
        }
        int index = Math.min(algoStep - 1, algoFlowSteps.size() - 1);
        return algoFlowSteps.get(index);
    }

    private boolean shouldForceWeakAnswerReview(String answerContent) {
        if (answerContent == null) {
            return true;
        }
        String normalized = answerContent.replaceAll("\\s+", "");
        if (normalized.length() <= 3) {
            return true;
        }
        if (normalized.matches("[0-9]+")) {
            return true;
        }
        return normalized.matches("[a-zA-Z]+");
    }

    private String buildWeakAnswerReviewContent(String mode, Integer turnNo, String answerContent) {
        String normalized = trimToNull(answerContent);
        String preview = normalized == null ? "（空）" : normalized;
        if ("ALGORITHM".equals(mode)) {
            return "第 " + turnNo + " 轮【点评结论】你的本轮回答“" + preview + "”信息量不足，无法判断你是否掌握该题核心思路。\n"
                    + "【参考答案】请至少覆盖：核心算法思路、关键步骤/边界处理、时间复杂度与空间复杂度。\n"
                    + "【改进建议】请按“思路 -> 步骤 -> 复杂度 -> 边界 case”四段结构重答，并给出一个可验证示例。";
        }
        return "第 " + turnNo + " 轮【点评结论】你的本轮回答“" + preview + "”信息量不足，无法评估你的综合能力。\n"
                + "【参考答案】请至少覆盖：项目背景、你的角色与决策、技术选型理由、量化结果。\n"
                + "【改进建议】请按“背景 -> 角色 -> 决策 -> 结果”四段结构重答，并给出一个可验证指标。";
    }

    private String buildWeakAnswerFollowupContent(String mode, String style, Integer nextTurnNo, Integer algoStep,
                                                  List<String> algoFlowSteps) {
        if ("ALGORITHM".equals(mode)) {
            String stepText = buildAlgorithmStepText(algoStep, algoFlowSteps);
            if ("PRESSURE".equals(style)) {
                return "第 " + nextTurnNo + " 轮追问：" + stepText + "。请先给出明确结论，再用一个样例证明。";
            }
            return "第 " + nextTurnNo + " 轮追问：" + stepText + "。请完整说明思路、复杂度和边界处理。";
        }
        if ("PRESSURE".equals(style)) {
            return "第 " + nextTurnNo + " 轮问题：请给出结构化回答，并补充可量化结果与风险兜底方案。";
        }
        return "第 " + nextTurnNo + " 轮问题：请按“结论-证据-细节-结果”结构补充完整回答。";
    }

    private String normalizeReviewType(String reviewType) {
        String value = trimToNull(reviewType);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if ("POSITIVE".equals(upper) || "NEUTRAL".equals(upper) || "IMPROVE".equals(upper)) {
            return upper;
        }
        return null;
    }

    private void updateSessionProgress(Long sessionId, Integer nextTurnNo, Integer algoStep, boolean isFinished, String updatedAt, String mode) {
        Map<String, String> payload = new HashMap<>();
        payload.put("currentTurn", String.valueOf(nextTurnNo));
        payload.put("updatedAt", updatedAt);
        if ("ALGORITHM".equals(mode)) {
            payload.put("algoCurrentStep", String.valueOf(algoStep));
        }
        if (isFinished) {
            payload.put("status", "FINISHED");
            payload.put("finishedAt", updatedAt);
        }
        stringRedisTemplate.opsForHash().putAll(SESSION_KEY_PREFIX + sessionId, payload);
    }

    private SubmitInterviewAnswerResponse.AssistantMessage buildAssistantMessage(Long id, Integer turnNo, String content,
                                                                                  Boolean typing, String createdAt) {
        SubmitInterviewAnswerResponse.AssistantMessage message = new SubmitInterviewAnswerResponse.AssistantMessage();
        message.setId(id);
        message.setRole("assistant");
        message.setKind("ASSISTANT");
        message.setTurnNo(turnNo);
        message.setContent(content);
        message.setContentFormat("MARKDOWN");
        message.setTyping(typing);
        message.setCreatedAt(createdAt);
        return message;
    }

    private SubmitInterviewAnswerResponse.AlgoFlow buildSubmitAnswerAlgoFlow(String mode, Integer currentStep, List<String> algoFlowSteps) {
        if (!"ALGORITHM".equals(mode)) {
            return null;
        }
        SubmitInterviewAnswerResponse.AlgoFlow flow = new SubmitInterviewAnswerResponse.AlgoFlow();
        flow.setEnabled(true);
        flow.setSteps(algoFlowSteps);
        flow.setCurrentStep(currentStep == null || currentStep < 1 ? 1 : currentStep);
        return flow;
    }

    private Map<String, Object> getStreamDataById(Long sessionId, String streamId) {
        Map<Object, Object> streamData = stringRedisTemplate.opsForHash().entries(STREAM_KEY_PREFIX + streamId);
        if (streamData == null || streamData.isEmpty()) {
            throw new BusinessException(404, "流不存在或已过期");
        }
        Long streamSessionId = parseLong(streamData.get("sessionId"));
        if (streamSessionId == null || !streamSessionId.equals(sessionId)) {
            throw new BusinessException(403, "无权限访问该流");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("streamId", streamId);
        result.put("targetMessageId", parseLong(streamData.get("targetMessageId")));
        result.put("finished", parseBoolean(streamData.get("finished")));
        return result;
    }

    private Map<String, Object> getLatestStreamMessage(Long sessionId) {
        List<String> messageIds = stringRedisTemplate.opsForList().range(buildSessionMessagesKey(sessionId), 0, -1);
        if (messageIds == null || messageIds.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("messageId", 0L);
            fallback.put("kind", "SYSTEM");
            fallback.put("content", "当前暂无可推送内容。");
            return fallback;
        }
        for (int i = messageIds.size() - 1; i >= 0; i--) {
            Long messageId = parseLong(messageIds.get(i));
            if (messageId == null) {
                continue;
            }
            Map<Object, Object> messageData = stringRedisTemplate.opsForHash().entries(buildSessionMessageKey(sessionId, messageId));
            if (messageData == null || messageData.isEmpty()) {
                continue;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("messageId", messageId);
            result.put("kind", defaultString(asString(messageData.get("kind")), "SYSTEM"));
            result.put("content", defaultString(asString(messageData.get("content")), ""));
            return result;
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("messageId", 0L);
        fallback.put("kind", "SYSTEM");
        fallback.put("content", "当前暂无可推送内容。");
        return fallback;
    }

    private Map<String, Object> getStreamMessageById(Long sessionId, Long messageId) {
        if (messageId == null || messageId <= 0) {
            throw new BusinessException("参数错误：messageId 非法");
        }
        Map<Object, Object> messageData = stringRedisTemplate.opsForHash().entries(buildSessionMessageKey(sessionId, messageId));
        if (messageData == null || messageData.isEmpty()) {
            throw new BusinessException(404, "消息不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("messageId", messageId);
        result.put("kind", defaultString(asString(messageData.get("kind")), "SYSTEM"));
        result.put("content", defaultString(asString(messageData.get("content")), ""));
        return result;
    }

    private String buildAssistantMarkdownContent(String reviewContent, String followupContent, boolean finished) {
        if (finished) {
            return "### 回答点评\n"
                    + defaultString(trimToNull(reviewContent), "本轮回答已收到。")
                    + "\n\n### 面试总结\n"
                    + defaultString(trimToNull(followupContent), "本场模拟面试已结束。");
        }
        return "### 回答点评\n"
                + defaultString(trimToNull(reviewContent), "本轮回答已收到。")
                + "\n\n### 下一问\n"
                + defaultString(trimToNull(followupContent), "请继续补充你的回答。");
    }

    private List<String> splitMarkdownContent(String content) {
        String normalized = content == null ? "" : content;
        if (normalized.isEmpty()) {
            return List.of("");
        }
        int chunkSize = 24;
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < normalized.length(); index += chunkSize) {
            int end = Math.min(index + chunkSize, normalized.length());
            chunks.add(normalized.substring(index, end));
        }
        return chunks;
    }

    private Map<String, Object> buildMessageStartEventData(Long messageId, Integer turnNo) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("kind", "ASSISTANT");
        eventData.put("messageId", messageId);
        eventData.put("turnNo", turnNo);
        eventData.put("contentFormat", "MARKDOWN");
        return eventData;
    }

    private Map<String, Object> buildDeltaEventData(Long messageId, String delta) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("kind", "ASSISTANT");
        eventData.put("messageId", messageId);
        eventData.put("delta", delta);
        return eventData;
    }

    private Map<String, Object> buildMessageDoneEventData(Long messageId) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("kind", "ASSISTANT");
        eventData.put("messageId", messageId);
        return eventData;
    }

    private Map<String, Object> buildDoneEventData(String streamId, boolean finished) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("streamId", streamId);
        eventData.put("finished", finished);
        return eventData;
    }

    private Map<String, Object> buildErrorEventData(Long messageId, String code, String message) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("kind", "ASSISTANT");
        eventData.put("messageId", messageId);
        eventData.put("code", code);
        eventData.put("message", message);
        return eventData;
    }

    private String resolveStreamErrorCode(Exception ex) {
        if (ex instanceof InterruptedException) {
            return "MODEL_TIMEOUT";
        }
        String message = ex.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("timeout") || lower.contains("timed out")) {
                return "MODEL_TIMEOUT";
            }
        }
        return "MODEL_ERROR";
    }

    private String resolveStreamErrorMessage(Exception ex) {
        String code = resolveStreamErrorCode(ex);
        if ("MODEL_TIMEOUT".equals(code)) {
            return "模型响应超时，已回退到兜底文案";
        }
        return "模型流式输出异常，已回退到兜底文案";
    }

    private String defaultString(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return value;
    }

    private void syncFinishedSessionToDatabase(Long sessionId, Map<Object, Object> sessionData, String finishedAt) {
        Long userId = parseLong(sessionData.get("userId"));
        if (userId == null) {
            return;
        }
        String mode = asString(sessionData.get("mode"));
        String style = asString(sessionData.get("style"));
        String title = asString(sessionData.get("title"));
        String startedAt = asString(sessionData.get("startedAt"));
        String dbStartedAt = toDatabaseDateTime(startedAt, finishedAt);
        String dbFinishedAt = toDatabaseDateTime(finishedAt, finishedAt);
        interviewSessionMapper.upsertFinishedSession(
                sessionId,
                userId,
                title,
                "ALGORITHM".equals(mode) ? 2 : 1,
                "ALGORITHM".equals(mode) ? 2 : 1,
                "PRESSURE".equals(style) ? 2 : 1,
                dbStartedAt,
                dbFinishedAt
        );
    }

    private String toDatabaseDateTime(String value, String fallback) {
        String text = value;
        if (text == null || text.isBlank()) {
            text = fallback;
        }
        if (text == null || text.isBlank()) {
            text = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return text;
        }
        String normalized = text.replace("T", " ");
        int dotIndex = normalized.indexOf('.');
        if (dotIndex > 0) {
            normalized = normalized.substring(0, dotIndex);
        }
        if (normalized.length() >= 19) {
            return normalized.substring(0, 19);
        }
        return normalized;
    }

    private String serializeAlgoFlowSteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        List<String> normalized = new ArrayList<>();
        for (String step : steps) {
            String value = trimToNull(step);
            if (value == null) {
                continue;
            }
            normalized.add(value.replace(ALGO_FLOW_STEPS_SEPARATOR, " "));
        }
        return String.join(ALGO_FLOW_STEPS_SEPARATOR, normalized);
    }

    private List<String> parseAlgoFlowSteps(Object value) {
        String text = trimToNull(asString(value));
        if (text == null) {
            return List.of("请先给出朴素解法", "请继续说明优化思路", "请分析时间与空间复杂度", "请补充边界 Case", "请给出同类题变式与迁移方法");
        }
        String[] parts = text.split("\\Q" + ALGO_FLOW_STEPS_SEPARATOR + "\\E");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String step = trimToNull(part);
            if (step == null) {
                continue;
            }
            result.add(step);
        }
        if (result.isEmpty()) {
            return List.of("请先给出朴素解法", "请继续说明优化思路", "请分析时间与空间复杂度", "请补充边界 Case", "请给出同类题变式与迁移方法");
        }
        return result;
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

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return null;
    }
}
