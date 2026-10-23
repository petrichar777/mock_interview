package com.interviewmaster.backend.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(
                stringRedisTemplate,
                new DefaultRedisScript<>("return 1", Long.class));
    }

    @Test
    void tryAcquire_underLimit_returnsAllowed() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        RateLimitResult result = rateLimitService.tryAcquire(
                "ratelimit:user:1:POST:/api/test", 5, Duration.ofSeconds(60));

        assertTrue(result.allowed());
        assertEquals(1L, result.currentCount());
        assertEquals(0L, result.retryAfterSeconds());
    }

    @Test
    void tryAcquire_overLimit_returnsRetryAfterSeconds() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-1500L);

        RateLimitResult result = rateLimitService.tryAcquire(
                "ratelimit:user:1:POST:/api/test", 5, Duration.ofSeconds(60));

        assertFalse(result.allowed());
        assertEquals(5, result.limit());
        assertEquals(2L, result.retryAfterSeconds());
    }

    @Test
    void tryAcquire_redisReturnsNull_failsOpen() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null);

        RateLimitResult result = rateLimitService.tryAcquire(
                "ratelimit:user:1:POST:/api/test", 5, Duration.ofSeconds(60));

        assertTrue(result.allowed());
    }

    @Test
    void tryAcquire_redisUnavailable_failsOpen() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        RateLimitResult result = rateLimitService.tryAcquire(
                "ratelimit:user:1:POST:/api/test", 5, Duration.ofSeconds(60));

        assertTrue(result.allowed());
    }

    @Test
    void tryAcquire_invalidArguments_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> rateLimitService.tryAcquire(" ", 5, Duration.ofSeconds(60)));
        assertThrows(IllegalArgumentException.class,
                () -> rateLimitService.tryAcquire("ratelimit:test", 0, Duration.ofSeconds(60)));
        assertThrows(IllegalArgumentException.class,
                () -> rateLimitService.tryAcquire("ratelimit:test", 5, Duration.ZERO));
    }
}
