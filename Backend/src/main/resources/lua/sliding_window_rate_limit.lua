local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowMillis = tonumber(ARGV[2])
local member = ARGV[3]

-- 用 Redis 服务端时间计算窗口起点，避免多实例机器时钟不一致
local redisTime = redis.call('TIME')
local nowMillis = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)
local windowStart = nowMillis - windowMillis

redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
local current = redis.call('ZCARD', key)

if current >= limit then
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfterMillis = windowMillis
    if oldest[2] ~= nil then
        retryAfterMillis = math.max(1, tonumber(oldest[2]) + windowMillis - nowMillis)
    end
    redis.call('PEXPIRE', key, windowMillis)
    return -retryAfterMillis
end

redis.call('ZADD', key, nowMillis, member)
redis.call('PEXPIRE', key, windowMillis)
return current + 1
