package com.interviewmaster.backend.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final long FAILURE_WARN_INTERVAL_MILLIS = 60_000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> slidingWindowRateLimitScript;
    private final AtomicLong lastFailureWarnAt = new AtomicLong(0L);

    /**
     * Redis 故障时选择放行：限流是保护措施，不应在缓存不可用时阻断全部业务。
     */
    public RateLimitResult tryAcquire(String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("限流 key 不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("限流次数必须大于 0");
        }
        if (window == null || window.toMillis() <= 0) {
            throw new IllegalArgumentException("限流窗口必须大于 0");
        }
        long windowMillis = window.toMillis();
        String member = UUID.randomUUID().toString();
        try {
            Long result = stringRedisTemplate.execute(
                    slidingWindowRateLimitScript,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(windowMillis),
                    member);
            if (result == null) {
                warnRedisUnavailable("script returned null");
                return new RateLimitResult(true, limit, 0, 0);
            }
            if (result > 0) {
                return new RateLimitResult(true, limit, result, 0);
            }
            long retryAfterSeconds = Math.max(1L, (-result + 999L) / 1000L);
            return new RateLimitResult(false, limit, limit, retryAfterSeconds);
        } catch (RuntimeException ex) {
            warnRedisUnavailable(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return new RateLimitResult(true, limit, 0, 0);
        }
    }

    private void warnRedisUnavailable(String reason) {
        long now = System.currentTimeMillis();
        long last = lastFailureWarnAt.get();
        if (now - last >= FAILURE_WARN_INTERVAL_MILLIS && lastFailureWarnAt.compareAndSet(last, now)) {
            log.warn("rate limit skipped because Redis is unavailable: {}", reason);
        }
    }
}
