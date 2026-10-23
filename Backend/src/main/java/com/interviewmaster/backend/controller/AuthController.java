package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.interviewmaster.backend.model.dto.request.LoginByCodeRequest;
import com.interviewmaster.backend.model.dto.request.LoginRequest;
import com.interviewmaster.backend.model.dto.request.ResetPasswordRequest;
import com.interviewmaster.backend.model.dto.request.RegisterRequest;
import com.interviewmaster.backend.model.dto.request.SendLoginCodeRequest;
import com.interviewmaster.backend.model.dto.request.SendResetCodeRequest;
import com.interviewmaster.backend.ratelimit.RateLimit;
import com.interviewmaster.backend.ratelimit.RateLimitDimension;
import com.interviewmaster.backend.service.AuthService;
import com.interviewmaster.backend.util.ApiResponse;
import com.interviewmaster.backend.util.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        String clientIp = WebUtils.getClientIp(httpRequest);
        Long userId = authService.register(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", userId)));
    }

    @RateLimit(key = "login-ip-minute", count = 5, period = 60,
            dimension = RateLimitDimension.IP, message = "登录尝试过于频繁，请稍后再试")
    @RateLimit(key = "login-ip-hour", count = 10, period = 3600,
            dimension = RateLimitDimension.IP, message = "登录尝试过于频繁，请稍后再试")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        String clientIp = WebUtils.getClientIp(httpRequest);
        Map<String, Object> data = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @RateLimit(key = "login-code-ip-minute", count = 5, period = 60,
            dimension = RateLimitDimension.IP, message = "验证码发送过于频繁，请稍后再试")
    @RateLimit(key = "login-code-ip-hour", count = 10, period = 3600,
            dimension = RateLimitDimension.IP, message = "验证码发送过于频繁，请稍后再试")
    @PostMapping("/login/code")
    public ResponseEntity<?> sendLoginCode(@Valid @RequestBody SendLoginCodeRequest request,
                                           HttpServletRequest httpRequest) {
        String clientIp = WebUtils.getClientIp(httpRequest);
        authService.sendLoginCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @RateLimit(key = "login-by-code-ip-minute", count = 5, period = 60,
            dimension = RateLimitDimension.IP, message = "登录尝试过于频繁，请稍后再试")
    @RateLimit(key = "login-by-code-ip-hour", count = 10, period = 3600,
            dimension = RateLimitDimension.IP, message = "登录尝试过于频繁，请稍后再试")
    @PostMapping("/login/by-code")
    public ResponseEntity<?> loginByCode(@Valid @RequestBody LoginByCodeRequest request,
                                         HttpServletRequest httpRequest) {
        String clientIp = WebUtils.getClientIp(httpRequest);
        Map<String, Object> data = authService.loginByCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @RateLimit(key = "reset-code-ip-minute", count = 5, period = 60,
            dimension = RateLimitDimension.IP, message = "验证码发送过于频繁，请稍后再试")
    @RateLimit(key = "reset-code-ip-hour", count = 10, period = 3600,
            dimension = RateLimitDimension.IP, message = "验证码发送过于频繁，请稍后再试")
    @PostMapping("/password/reset-code")
    public ResponseEntity<?> sendResetCode(@Valid @RequestBody SendResetCodeRequest request,
                                           HttpServletRequest httpRequest) {
        String clientIp = WebUtils.getClientIp(httpRequest);
        authService.sendResetCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @RateLimit(key = "reset-password-ip-minute", count = 5, period = 60,
            dimension = RateLimitDimension.IP, message = "操作过于频繁，请稍后再试")
    @RateLimit(key = "reset-password-ip-hour", count = 10, period = 3600,
            dimension = RateLimitDimension.IP, message = "操作过于频繁，请稍后再试")
    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
