package com.interviewmaster.backend.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.interviewmaster.backend.controller.AuthController;
import com.interviewmaster.backend.controller.InterviewController;
import com.interviewmaster.backend.controller.PracticeController;
import com.interviewmaster.backend.model.dto.request.CreateInterviewSessionRequest;
import com.interviewmaster.backend.model.dto.request.LoginRequest;
import com.interviewmaster.backend.model.dto.request.PracticeSubmitAnswerRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

class RateLimitWiringTest {

    @Test
    void login_hasMinuteAndHourIpRules() throws Exception {
        Map<String, RateLimit> rules = rulesOf(
                AuthController.class, "login", LoginRequest.class, HttpServletRequest.class);

        assertEquals(Set.of("login-ip-minute", "login-ip-hour"), rules.keySet());
        assertRule(rules.get("login-ip-minute"), 5, 60, RateLimitDimension.IP);
        assertRule(rules.get("login-ip-hour"), 10, 3600, RateLimitDimension.IP);
    }

    @Test
    void practiceSubmit_hasUserRule() throws Exception {
        Map<String, RateLimit> rules = rulesOf(
                PracticeController.class, "submitAnswer", Long.class, PracticeSubmitAnswerRequest.class);

        assertEquals(1, rules.size());
        assertRule(rules.get("practice-submit"), 60, 60, RateLimitDimension.USER);
    }

    @Test
    void interviewCreate_hasUserRule() throws Exception {
        Map<String, RateLimit> rules = rulesOf(
                InterviewController.class, "createSession", CreateInterviewSessionRequest.class);

        assertEquals(1, rules.size());
        assertRule(rules.get("interview-create"), 5, 600, RateLimitDimension.USER);
    }

    private Map<String, RateLimit> rulesOf(Class<?> type, String methodName, Class<?>... params)
            throws NoSuchMethodException {
        Set<RateLimit> rules = AnnotatedElementUtils.findMergedRepeatableAnnotations(
                type.getMethod(methodName, params), RateLimit.class);
        return rules.stream().collect(Collectors.toMap(RateLimit::key, Function.identity()));
    }

    private void assertRule(RateLimit rule, int count, int period, RateLimitDimension dimension) {
        assertEquals(count, rule.count());
        assertEquals(period, rule.period());
        assertEquals(dimension, rule.dimension());
    }
}
