package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.model.dto.response.CurrentUserResponse;
import com.interviewmaster.backend.service.UserService;
import com.interviewmaster.backend.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @SaCheckLogin
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUserInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        CurrentUserResponse data = userService.getCurrentUserInfo(userId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
