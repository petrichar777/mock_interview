package com.interviewmaster.backend.ratelimit;

import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.model.entity.RateLimitLog;
import com.interviewmaster.backend.util.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_LIMIT_KEY_LENGTH = 200;
    private static final int MAX_URI_LENGTH = 255;
    private static final int MAX_IP_LENGTH = 64;

    private final RateLimitService rateLimitService;
    private final RateLimitLogService rateLimitLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Set<RateLimit> rules = AnnotatedElementUtils.findMergedRepeatableAnnotations(
                handlerMethod.getMethod(), RateLimit.class);
        if (rules.isEmpty()) {
            rules = AnnotatedElementUtils.findMergedRepeatableAnnotations(
                    handlerMethod.getBeanType(), RateLimit.class);
        }
        for (RateLimit rule : rules) {
            enforce(rule, request, response);
        }
        return true;
    }

    private void enforce(RateLimit rule, HttpServletRequest request, HttpServletResponse response) {
        LimitTarget target = resolveTarget(request, rule.dimension());
        String key = buildKey(rule, target, request);
        RateLimitResult result = rateLimitService.tryAcquire(
                key, rule.count(), Duration.ofSeconds(rule.period()));
        if (result.allowed()) {
            return;
        }
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        log.warn("rate limit hit, key={}, userId={}, clientIp={}, uri={}",
                key, target.userId(), target.clientIp(), request.getRequestURI());
        recordViolation(rule, target, result, key, request);
        throw new BusinessException(429, rule.message());
    }

    private LimitTarget resolveTarget(HttpServletRequest request, RateLimitDimension dimension) {
        String clientIp = WebUtils.getClientIp(request);
        return switch (dimension) {
            case USER -> {
                Long userId = currentUserId();
                if (userId == null) {
                    yield new LimitTarget(RateLimitDimension.IP, clientIp, null, clientIp);
                }
                yield new LimitTarget(RateLimitDimension.USER, String.valueOf(userId), userId, clientIp);
            }
            case ACCOUNT -> {
                String account = firstNonBlank(
                        request.getHeader("X-Account"), request.getParameter("account"));
                if (account == null) {
                    yield new LimitTarget(RateLimitDimension.IP, clientIp, null, clientIp);
                }
                yield new LimitTarget(
                        RateLimitDimension.ACCOUNT,
                        sha256(account.trim().toLowerCase(Locale.ROOT)),
                        null,
                        clientIp);
            }
            case GLOBAL -> new LimitTarget(RateLimitDimension.GLOBAL, "global", null, clientIp);
            default -> new LimitTarget(RateLimitDimension.IP, clientIp, null, clientIp);
        };
    }

    private Long currentUserId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? null : Long.valueOf(loginId.toString());
        } catch (Exception ex) {
            log.debug("failed to resolve login id for rate limit", ex);
            return null;
        }
    }

    private String buildKey(RateLimit rule, LimitTarget target, HttpServletRequest request) {
        String suffix = rule.key().isBlank() ? "" : ":" + rule.key().trim();
        // 使用路由模板而非原始 URI，避免路径参数（如房间码）制造出大量独立限流键
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern instanceof String value ? value : request.getRequestURI();
        return "ratelimit:"
                + target.dimension().name().toLowerCase(Locale.ROOT) + ":"
                + target.identifier() + ":"
                + request.getMethod() + ":"
                + path
                + suffix;
    }

    private void recordViolation(RateLimit rule, LimitTarget target, RateLimitResult result,
                                 String key, HttpServletRequest request) {
        RateLimitLog entry = new RateLimitLog();
        entry.setLimitKey(truncate(key, MAX_LIMIT_KEY_LENGTH));
        entry.setDimension(target.dimension().name());
        entry.setUserId(target.userId());
        entry.setClientIp(truncate(target.clientIp(), MAX_IP_LENGTH));
        entry.setRequestUri(truncate(request.getRequestURI(), MAX_URI_LENGTH));
        entry.setRequestMethod(request.getMethod());
        entry.setLimitCount(rule.count());
        entry.setWindowSeconds(rule.period());
        entry.setCurrentCount((int) result.currentCount());
        rateLimitLogService.save(entry);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record LimitTarget(RateLimitDimension dimension, String identifier,
                               Long userId, String clientIp) {
    }
}
