package com.interviewmaster.backend.util;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 原子化 Redis 操作工具，供多个 Service 共用。
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 原子递增并仅在首次创建时设置过期时间。
     * Lua 脚本保证 INCR + EXPIRE 的原子性，用于频率限制（如每日面试次数、IP 限制等）。
     *
     * @return 递增后的当前计数值
     */
    public Long incrementWithExpiry(String key, Duration expiry) {
        String luaScript =
                "local current = redis.call('INCR', KEYS[1]) " +
                "if current == 1 then " +
                "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                "end " +
                "return current";
        Long ttlSeconds = expiry.getSeconds();
        Object result = stringRedisTemplate.execute(
                new DefaultRedisScript<>(luaScript, Long.class),
                List.of(key),
                String.valueOf(ttlSeconds)
        );
        if (result instanceof Long l) {
            return l;
        }
        if (result instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void set(String key, String value, Duration ttl) {
        requirePositive(ttl);
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    /**
     * 尝试获取分布式锁，返回锁令牌；未获取到返回 null。
     * 调用方必须使用返回的令牌释放，避免锁过期后误删其他线程持有的锁。
     */
    public String tryLock(String key, Duration ttl) {
        requirePositive(ttl);
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public boolean releaseLock(String key, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Object result = stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(key), token);
        return result instanceof Number n && n.longValue() > 0;
    }

    private void requirePositive(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Redis TTL 必须大于 0");
        }
    }
}
