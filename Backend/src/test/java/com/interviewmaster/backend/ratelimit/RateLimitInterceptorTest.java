package com.interviewmaster.backend.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.model.entity.RateLimitLog;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private RateLimitLogService rateLimitLogService;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(rateLimitService, rateLimitLogService);
    }

    @Test
    void preHandle_noAnnotation_passesWithoutRateLimit() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(new PlainController(), "plain");
        MockHttpServletRequest request = request("GET", "/api/plain");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod));
        verifyNoInteractions(rateLimitService, rateLimitLogService);
    }

    @Test
    void preHandle_underLimit_returnsTrue() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(new LimitedController(), "limited");
        MockHttpServletRequest request = request("POST", "/api/test");
        when(rateLimitService.tryAcquire(anyString(), eq(5), eq(Duration.ofSeconds(60))))
                .thenReturn(new RateLimitResult(true, 5, 1, 0));

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).tryAcquire(keyCaptor.capture(), eq(5), eq(Duration.ofSeconds(60)));
        assertTrue(keyCaptor.getValue().startsWith("ratelimit:ip:"));
        assertTrue(keyCaptor.getValue().contains("POST:/api/test"));
        assertTrue(keyCaptor.getValue().endsWith(":test"));
    }

    @Test
    void preHandle_usesRoutePatternInsteadOfRawUri() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(new LimitedController(), "limited");
        MockHttpServletRequest request = request("POST", "/api/rooms/ABC123");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/rooms/{roomCode}");
        when(rateLimitService.tryAcquire(anyString(), eq(5), eq(Duration.ofSeconds(60))))
                .thenReturn(new RateLimitResult(true, 5, 1, 0));

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).tryAcquire(keyCaptor.capture(), eq(5), eq(Duration.ofSeconds(60)));
        assertTrue(keyCaptor.getValue().contains("POST:/api/rooms/{roomCode}"));
    }

    @Test
    void preHandle_overLimit_setsRetryAfterAndThrows429() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(new LimitedController(), "limited");
        MockHttpServletRequest request = request("POST", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimitService.tryAcquire(anyString(), eq(5), eq(Duration.ofSeconds(60))))
                .thenReturn(new RateLimitResult(false, 5, 5, 42));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, handlerMethod));

        assertEquals(429, ex.getCode());
        assertEquals("42", response.getHeader("Retry-After"));
        ArgumentCaptor<RateLimitLog> logCaptor = ArgumentCaptor.forClass(RateLimitLog.class);
        verify(rateLimitLogService).save(logCaptor.capture());
        assertEquals("IP", logCaptor.getValue().getDimension());
        assertEquals(5, logCaptor.getValue().getLimitCount());
        assertEquals(60, logCaptor.getValue().getWindowSeconds());
    }

    @Test
    void preHandle_repeatableRules_appliesAllRules() throws Exception {
        HandlerMethod handlerMethod = handlerMethod(new MultiRuleController(), "multi");
        MockHttpServletRequest request = request("GET", "/api/multi");
        when(rateLimitService.tryAcquire(anyString(), eq(2), eq(Duration.ofSeconds(60))))
                .thenReturn(new RateLimitResult(true, 2, 1, 0));
        when(rateLimitService.tryAcquire(anyString(), eq(100), eq(Duration.ofSeconds(60))))
                .thenReturn(new RateLimitResult(true, 100, 1, 0));

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod));

        verify(rateLimitService).tryAcquire(anyString(), eq(2), any(Duration.class));
        verify(rateLimitService).tryAcquire(anyString(), eq(100), any(Duration.class));
    }

    private HandlerMethod handlerMethod(Object bean, String methodName) throws NoSuchMethodException {
        Method method = bean.getClass().getDeclaredMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    private MockHttpServletRequest request(String httpMethod, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(httpMethod);
        request.setRequestURI(uri);
        request.setRemoteAddr("192.0.2.20");
        return request;
    }

    private static class PlainController {
        public void plain() {
        }
    }

    private static class LimitedController {
        @RateLimit(key = "test", count = 5, period = 60, dimension = RateLimitDimension.IP)
        public void limited() {
        }
    }

    private static class MultiRuleController {
        @RateLimit(key = "ip-rule", count = 2, period = 60, dimension = RateLimitDimension.IP)
        @RateLimit(key = "global-rule", count = 100, period = 60, dimension = RateLimitDimension.GLOBAL)
        public void multi() {
        }
    }
}
