package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.interviewmaster.backend.model.dto.request.LoginByCodeRequest;
import com.interviewmaster.backend.model.dto.request.LoginRequest;
import com.interviewmaster.backend.model.dto.request.ResetPasswordRequest;
import com.interviewmaster.backend.model.dto.request.RegisterRequest;
import com.interviewmaster.backend.model.dto.request.SendLoginCodeRequest;
import com.interviewmaster.backend.model.dto.request.SendResetCodeRequest;
import com.interviewmaster.backend.service.AuthService;
import com.interviewmaster.backend.util.ApiResponse;
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
        String clientIp = getClientIp(httpRequest);
        Long userId = authService.register(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", userId)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        Map<String, Object> data = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/login/code")
    public ResponseEntity<?> sendLoginCode(@Valid @RequestBody SendLoginCodeRequest request,
                                           HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        authService.sendLoginCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/login/by-code")
    public ResponseEntity<?> loginByCode(@Valid @RequestBody LoginByCodeRequest request,
                                         HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        Map<String, Object> data = authService.loginByCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/password/reset-code")
    public ResponseEntity<?> sendResetCode(@Valid @RequestBody SendResetCodeRequest request,
                                           HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        authService.sendResetCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "unknown" : ip;
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
