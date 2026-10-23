package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.model.dto.response.CheckinHeatmapResponse;
import com.interviewmaster.backend.model.dto.response.CheckinResultResponse;
import com.interviewmaster.backend.service.CheckinService;
import com.interviewmaster.backend.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @SaCheckLogin
    @PostMapping
    public ResponseEntity<?> checkinToday() {
        Long userId = StpUtil.getLoginIdAsLong();
        CheckinResultResponse data = checkinService.checkinToday(userId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @GetMapping("/heatmap")
    public ResponseEntity<?> getHeatmap(@RequestParam("month") String month) {
        Long userId = StpUtil.getLoginIdAsLong();
        CheckinHeatmapResponse data = checkinService.getHeatmap(userId, month);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
