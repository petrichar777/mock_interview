package com.interviewmaster.backend.ratelimit;

import com.interviewmaster.backend.mapper.RateLimitLogMapper;
import com.interviewmaster.backend.model.entity.RateLimitLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitLogService {

    private final RateLimitLogMapper rateLimitLogMapper;

    /**
     * 日志落库失败不能影响限流响应，因此吞掉异常只记录 warn。
     */
    @Async("rateLimitLogExecutor")
    public void save(RateLimitLog entry) {
        try {
            rateLimitLogMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("failed to persist rate limit log: {}", ex.getMessage());
        }
    }
}
