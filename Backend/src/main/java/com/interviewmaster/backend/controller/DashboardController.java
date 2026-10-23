package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.model.dto.response.DashboardRecommendedPracticesResponse;
import com.interviewmaster.backend.model.dto.response.DashboardSummaryResponse;
import com.interviewmaster.backend.service.DashboardService;
import com.interviewmaster.backend.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @SaCheckLogin
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary() {
        Long userId = StpUtil.getLoginIdAsLong();
        DashboardSummaryResponse data = dashboardService.getSummary(userId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/recommended-practices")
    public ResponseEntity<?> getRecommendedPractices(@RequestParam(required = false) Integer limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        DashboardRecommendedPracticesResponse data = dashboardService.getRecommendedPractices(userId, limit);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
