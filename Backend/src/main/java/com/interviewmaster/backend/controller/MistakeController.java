package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.model.dto.request.MistakeRandomRequest;
import com.interviewmaster.backend.model.dto.response.MistakeDetailResponse;
import com.interviewmaster.backend.model.dto.response.MistakeListResponse;
import com.interviewmaster.backend.model.dto.response.MistakeModuleStatsResponse;
import com.interviewmaster.backend.model.dto.response.MistakeRandomResponse;
import com.interviewmaster.backend.model.dto.response.MistakeRemoveResponse;
import com.interviewmaster.backend.model.dto.response.MistakeStatsResponse;
import com.interviewmaster.backend.service.MistakeService;
import com.interviewmaster.backend.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mistakes")
@RequiredArgsConstructor
public class MistakeController {

    private final MistakeService mistakeService;

    @SaCheckLogin
    @GetMapping
    public ResponseEntity<?> listMistakes(@RequestParam(required = false) Long moduleId,
                                          @RequestParam Integer page,
                                          @RequestParam Integer size,
                                          @RequestParam(required = false) String sortBy) {
        Long userId = StpUtil.getLoginIdAsLong();
        MistakeListResponse data = mistakeService.listMistakes(userId, moduleId, page, size, sortBy);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/random")
    public ResponseEntity<?> randomMistakes(@RequestBody(required = false) MistakeRandomRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        Integer count = request == null ? null : request.getCount();
        Long moduleId = request == null ? null : request.getModuleId();
        MistakeRandomResponse data = mistakeService.randomMistakes(userId, count, moduleId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> removeMistake(@PathVariable Long questionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        MistakeRemoveResponse data = mistakeService.removeMistake(userId, questionId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Long userId = StpUtil.getLoginIdAsLong();
        MistakeStatsResponse data = mistakeService.getStats(userId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/module-stats")
    public ResponseEntity<?> getModuleStats(@RequestParam(required = false) Integer limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<MistakeModuleStatsResponse> data = mistakeService.getModuleStats(userId, limit);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/questions/{questionId}")
    public ResponseEntity<?> getMistakeDetail(@PathVariable Long questionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        MistakeDetailResponse data = mistakeService.getMistakeDetail(userId, questionId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
