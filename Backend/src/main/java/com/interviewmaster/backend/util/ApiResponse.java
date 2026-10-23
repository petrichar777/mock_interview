package com.interviewmaster.backend.util;

import java.util.HashMap;
import java.util.Map;

public final class ApiResponse {

    private ApiResponse() {
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        body.put("message", "OK");
        body.put("data", data);
        return body;
    }

    public static Map<String, Object> fail(int code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }
}
