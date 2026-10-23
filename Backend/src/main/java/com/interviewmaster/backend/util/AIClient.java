package com.interviewmaster.backend.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.exception.BusinessException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public class AIClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final long requestTimeoutMs;
    private final int requestRetry;
    private final ObjectMapper objectMapper;

    public AIClient(WebClient webClient, String baseUrl, String apiKey,
                    String model, long requestTimeoutMs, int requestRetry,
                    ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.requestTimeoutMs = requestTimeoutMs;
        this.requestRetry = requestRetry;
        this.objectMapper = objectMapper;
    }

    public String chat(String systemPrompt, String userPrompt, double temperature) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(500, "AI API Key 未配置");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("temperature", temperature);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        Map<String, Object> response = executeChatRequest(payload);
        if (response == null) {
            throw new BusinessException(500, "AI 响应为空");
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new BusinessException(500, "AI 响应格式错误");
        }
        Object firstObj = choices.get(0);
        if (!(firstObj instanceof Map<?, ?> firstChoice)) {
            throw new BusinessException(500, "AI 响应格式错误");
        }
        Object messageObj = firstChoice.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            throw new BusinessException(500, "AI 响应格式错误");
        }
        Object contentObj = message.get("content");
        String content = (contentObj != null && !contentObj.toString().isBlank())
                ? contentObj.toString().trim() : null;
        if (content == null) {
            throw new BusinessException(500, "AI 返回内容为空");
        }
        return content.trim();
    }

    private Map<String, Object> executeChatRequest(Map<String, Object> payload) {
        Exception lastException = null;
        int retry = Math.max(0, requestRetry);
        for (int attempt = 0; attempt <= retry; attempt++) {
            try {
                Mono<Map<String, Object>> request = webClient.post()
                        .uri(baseUrl + "/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .timeout(Duration.ofMillis(resolveTimeoutMs()));
                Map<String, Object> response = request.block();
                if (response != null) {
                    return response;
                }
                lastException = new BusinessException(500, "AI 响应为空");
            } catch (Exception ex) {
                lastException = ex;
            }
        }
        String message = lastException == null ? "AI 请求失败" : defaultErrorMessage(lastException);
        throw new BusinessException(500, message);
    }

    private long resolveTimeoutMs() {
        if (requestTimeoutMs < 1000) {
            return 1000L;
        }
        return requestTimeoutMs;
    }

    private String defaultErrorMessage(Exception ex) {
        String text = ex.getMessage();
        if (text == null || text.isBlank()) {
            return "AI 请求失败";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("timeout")) {
            return "AI 请求超时";
        }
        return "AI 请求失败：" + text;
    }

    public Map<String, Object> parseJsonContent(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = content.substring(start, end + 1);
            try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
            }
        }
        throw new BusinessException(500, "AI 返回 JSON 解析失败");
    }
}
