package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.model.dto.request.PracticeSubmitAnswerRequest;
import com.interviewmaster.backend.model.dto.response.PracticeAddMistakeResponse;
import com.interviewmaster.backend.model.dto.response.PracticeModuleListResponse;
import com.interviewmaster.backend.model.dto.response.PracticeNextQuestionResponse;
import com.interviewmaster.backend.model.dto.response.PracticeSubmitAnswerResponse;
import com.interviewmaster.backend.ratelimit.RateLimit;
import com.interviewmaster.backend.ratelimit.RateLimitDimension;
import com.interviewmaster.backend.service.PracticeService;
import com.interviewmaster.backend.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;

    @SaCheckLogin
    @GetMapping("/modules")
    public ResponseEntity<?> listModules(@RequestParam(required = false) String category) {
        Long userId = StpUtil.getLoginIdAsLong();
        PracticeModuleListResponse data = practiceService.listModules(userId, category);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/modules/{moduleId}/next")
    public ResponseEntity<?> getNextQuestion(@PathVariable Long moduleId,
                                             @RequestParam(required = false) String mode) {
        Long userId = StpUtil.getLoginIdAsLong();
        PracticeNextQuestionResponse data = practiceService.getNextQuestion(userId, moduleId, mode);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @RateLimit(key = "practice-submit", count = 60, period = 60,
            dimension = RateLimitDimension.USER, message = "答题提交过于频繁，请稍后再试")
    @PostMapping("/questions/{questionId}/submit")
    public ResponseEntity<?> submitAnswer(@PathVariable Long questionId,
                                          @Valid @RequestBody PracticeSubmitAnswerRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        PracticeSubmitAnswerResponse data = practiceService.submitAnswer(userId, questionId, request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/modules/{moduleId}/prev")
    public ResponseEntity<?> getPrevQuestion(@PathVariable Long moduleId,
                                             @RequestParam(required = false) String mode) {
        Long userId = StpUtil.getLoginIdAsLong();
        PracticeNextQuestionResponse data = practiceService.getPrevQuestion(userId, moduleId, mode);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/questions/{questionId}/mistake")
    public ResponseEntity<?> addMistake(@PathVariable Long questionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        PracticeAddMistakeResponse data = practiceService.addMistake(userId, questionId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
