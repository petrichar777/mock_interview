package com.interviewmaster.backend.util;

import jakarta.servlet.http.HttpServletRequest;

public final class WebUtils {

    private static final int MAX_IP_LENGTH = 64;

    private WebUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isBlank(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isBlank(ip)) {
            ip = request.getRemoteAddr();
        }
        if (isBlank(ip)) {
            return "unknown";
        }
        String first = ip.split(",")[0].trim();
        if (first.isEmpty()) {
            return "unknown";
        }
        return first.length() > MAX_IP_LENGTH ? first.substring(0, MAX_IP_LENGTH) : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value.trim());
    }
}
