package com.interviewmaster.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisUtil = new RedisUtil(stringRedisTemplate);
    }

    @Test
    void tryLock_acquired_returnsToken() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        String token = redisUtil.tryLock("lock:room:1", Duration.ofSeconds(30));

        assertNotNull(token);
        verify(valueOperations).setIfAbsent(eq("lock:room:1"), eq(token), eq(Duration.ofSeconds(30)));
    }

    @Test
    void tryLock_notAcquired_returnsNull() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertNull(redisUtil.tryLock("lock:room:1", Duration.ofSeconds(30)));
    }

    @Test
    void releaseLock_matchingToken_returnsTrue() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        assertTrue(redisUtil.releaseLock("lock:room:1", "token-1"));
    }

    @Test
    void releaseLock_blankToken_returnsFalseWithoutRedis() {
        assertFalse(redisUtil.releaseLock("lock:room:1", " "));
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void incrementWithExpiry_returnsCurrentCount() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(3L);

        assertEquals(3L, redisUtil.incrementWithExpiry("interview:daily:1", Duration.ofDays(1)));
    }

    @Test
    void set_invalidTtl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> redisUtil.set("key", "value", Duration.ZERO));
        verifyNoInteractions(stringRedisTemplate);
    }
}
