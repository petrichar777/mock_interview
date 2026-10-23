package com.interviewmaster.backend.ratelimit;

public record RateLimitResult(boolean allowed, int limit, long currentCount, long retryAfterSeconds) {
}
