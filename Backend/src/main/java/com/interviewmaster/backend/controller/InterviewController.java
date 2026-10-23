package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.model.dto.request.CreateInterviewSessionRequest;
import com.interviewmaster.backend.model.dto.request.FinishInterviewRequest;
import com.interviewmaster.backend.model.dto.request.ResumeOptimizeRequest;
import com.interviewmaster.backend.model.dto.response.EvaluateInterviewResponse;
import com.interviewmaster.backend.model.dto.response.InterviewReportListResponse;
import com.interviewmaster.backend.model.dto.response.QuotaInfoResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeAsyncResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeResponse;
import com.interviewmaster.backend.model.dto.response.ResumeOptimizeResultResponse;
import com.interviewmaster.backend.model.dto.request.SubmitInterviewAnswerRequest;
import com.interviewmaster.backend.model.dto.response.CreateInterviewSessionResponse;
import com.interviewmaster.backend.model.dto.response.FinishInterviewResponse;
import com.interviewmaster.backend.model.dto.response.InterviewSessionDetailResponse;
import com.interviewmaster.backend.model.dto.response.InterviewSessionMessageResponse;
import com.interviewmaster.backend.model.dto.response.ResumeParseResponse;
import com.interviewmaster.backend.model.dto.response.SubmitInterviewAnswerResponse;
import java.util.List;
import com.interviewmaster.backend.service.InterviewService;
import com.interviewmaster.backend.service.ResumeService;
import com.interviewmaster.backend.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final ResumeService resumeService;
    private final InterviewService interviewService;

    @SaCheckLogin
    @PostMapping("/resume/parse")
    public ResponseEntity<?> parseResume(@RequestParam("file") MultipartFile file) {
        Long userId = StpUtil.getLoginIdAsLong();
        ResumeParseResponse data = resumeService.parseResume(userId, file);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/resume/optimize")
    public ResponseEntity<?> optimizeResume(@Valid @RequestBody ResumeOptimizeRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        ResumeOptimizeResponse data = interviewService.optimizeResume(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/resume/optimize-async")
    public ResponseEntity<?> optimizeResumeAsync(@Valid @RequestBody ResumeOptimizeRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        ResumeOptimizeAsyncResponse data = interviewService.optimizeResumeAsync(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/resume/optimize-result/{taskId}")
    public ResponseEntity<?> getOptimizeResult(@PathVariable String taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        ResumeOptimizeResultResponse data = interviewService.getOptimizeResult(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/resume/optimize-latest")
    public ResponseEntity<?> getLatestOptimization() {
        Long userId = StpUtil.getLoginIdAsLong();
        ResumeOptimizeResultResponse data = interviewService.getLatestOptimization(userId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/resume/quota")
    public ResponseEntity<?> getResumeQuota() {
        Long userId = StpUtil.getLoginIdAsLong();
        QuotaInfoResponse data = interviewService.getResumeQuota(userId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@Valid @RequestBody CreateInterviewSessionRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        CreateInterviewSessionResponse data = interviewService.createSession(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSessionInfo(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        InterviewSessionDetailResponse data = interviewService.getSessionInfo(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<?> getSessionMessages(@PathVariable Long sessionId,
                                                @RequestParam(required = false) Long sinceId,
                                                @RequestParam(required = false) Integer limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<InterviewSessionMessageResponse> data = interviewService.getSessionMessages(userId, sessionId, sinceId, limit);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/sessions/{sessionId}/answer")
    public ResponseEntity<?> submitAnswer(@PathVariable Long sessionId,
                                          @Valid @RequestBody SubmitInterviewAnswerRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        SubmitInterviewAnswerResponse data = interviewService.submitAnswer(userId, sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/sessions/{sessionId}/finish")
    public ResponseEntity<?> finishSession(@PathVariable Long sessionId,
                                           @Valid @RequestBody(required = false) FinishInterviewRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        FinishInterviewResponse data = interviewService.finishSession(userId, sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/sessions/{sessionId}/evaluate")
    public ResponseEntity<?> evaluateInterview(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        EvaluateInterviewResponse data = interviewService.evaluate(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/reports")
    public ResponseEntity<?> listReports(@RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String mode) {
        Long userId = StpUtil.getLoginIdAsLong();
        InterviewReportListResponse data = interviewService.listReports(userId, page, size, mode);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/reports/trend")
    public ResponseEntity<?> trendScores(@RequestParam(defaultValue = "15") int limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        java.util.List<InterviewReportListResponse.ReportItem> data = interviewService.getTrendScores(userId, limit);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/reports/{sessionId}/pdf")
    public ResponseEntity<byte[]> exportReportPdf(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        byte[] pdf = interviewService.generateReportPdf(userId, sessionId);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"interview-report.pdf\"")
                .body(pdf);
    }

    @SaCheckLogin
    @GetMapping("/reports/{sessionId}")
    public ResponseEntity<?> getReportDetail(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        EvaluateInterviewResponse data = interviewService.getReportDetail(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping(value = "/sessions/{sessionId}/stream-answer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnswer(@PathVariable Long sessionId,
                                    @Valid @RequestBody SubmitInterviewAnswerRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        return interviewService.streamAnswer(userId, sessionId, request);
    }

    @SaCheckLogin
    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSession(@PathVariable Long sessionId,
                                    @RequestParam(required = false) String streamId,
                                    @RequestParam(required = false) Long messageId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return interviewService.streamSession(userId, sessionId, streamId, messageId);
    }
}
