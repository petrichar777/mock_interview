package com.interviewmaster.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WebUtilsTest {

    @Test
    void getClientIp_usesFirstForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", " 203.0.113.7 , 10.0.0.1");

        assertEquals("203.0.113.7", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_fallsBackToRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.9");

        assertEquals("198.51.100.9", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");

        assertEquals("192.0.2.10", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_unknownHeader_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.11");
        request.addHeader("X-Forwarded-For", "unknown");

        assertEquals("192.0.2.11", WebUtils.getClientIp(request));
    }

    @Test
    void getClientIp_tooLong_truncatesToMaxLength() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "a".repeat(100));

        assertEquals(64, WebUtils.getClientIp(request).length());
    }
}
