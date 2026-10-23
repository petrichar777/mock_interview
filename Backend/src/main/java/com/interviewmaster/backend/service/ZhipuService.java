package com.interviewmaster.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.util.AIClient;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZhipuService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${zhipu.base-url}")
    private String zhipuBaseUrl;

    @Value("${zhipu.api-key}")
    private String zhipuApiKey;

    @Value("${zhipu.model:glm-4.7-flashx}")
    private String zhipuModel;

    @Value("${zhipu.request-timeout-ms:120000}")
    private long zhipuRequestTimeoutMs;

    @Value("${zhipu.request-retry:1}")
    private int zhipuRequestRetry;

    private AIClient aiClient;

    @PostConstruct
    void init() {
        this.aiClient = new AIClient(webClient, zhipuBaseUrl, zhipuApiKey,
                zhipuModel, zhipuRequestTimeoutMs, zhipuRequestRetry, objectMapper);
    }

    public ReviewResult reviewQuestion(String questionForReview) {
        String systemPrompt = "你是一位资深技术面试官和出题审核专家。"
                + "请审查以下面试题目的质量，判断其是否正确、准确、可用。"
                + "\n审查维度："
                + "\n1) 答案正确性：正确答案是否确实正确，是否有事实性错误或概念混淆"
                + "\n2) 干扰项合理性：错误选项是否有迷惑性且不会过于离谱，对于单选题是否恰好只有1个正确答案"
                + "\n3) 解析准确性：解析文本是否准确、清晰，是否解释了每个选项为什么对/错"
                + "\n4) 题干清晰度：题目描述是否清晰无歧义，技术术语使用是否准确"
                + "\n5) 难度匹配：题目实际难度是否与标注难度一致"
                + "\n你必须严格输出 JSON，不要 Markdown、不要代码块、不要任何额外文本："
                + "\n{\"correct\":true,\"confidence\":8,\"issues\":[\"具体问题描述1\",\"具体问题描述2\"]}"
                + "\n字段说明：correct 为 true 表示题目质量合格可以上架，false 表示题目有问题需要修改或删除；"
                + "confidence 为 1-10 的整数，表示你对判断的置信度，10 表示非常确定；"
                + "issues 是字符串数组，列举发现的具体问题。如果 correct 为 true 且无问题，issues 可以为空数组。";
        String content = aiClient.chat(systemPrompt, questionForReview, 0.2);
        Map<String, Object> payload = aiClient.parseJsonContent(content);

        ReviewResult result = new ReviewResult();
        Boolean correct = asBoolean(payload.get("correct"));
        result.setCorrect(correct != null && correct);
        result.setConfidence(asIntegerWithDefault(payload.get("confidence"), 5));
        result.setIssues(parseIssues(payload.get("issues")));
        return result;
    }

    private List<String> parseIssues(Object issuesObj) {
        List<String> issues = new ArrayList<>();
        if (issuesObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item != null && !item.toString().isBlank()) {
                    issues.add(item.toString());
                }
            }
        }
        return issues;
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = value.toString();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return null;
    }

    private int asIntegerWithDefault(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            int v = number.intValue();
            return v < 1 ? 1 : Math.min(v, 10);
        }
        try {
            int v = Integer.parseInt(value.toString());
            return v < 1 ? 1 : Math.min(v, 10);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Data
    public static class ReviewResult {
        private boolean correct;
        private int confidence;
        private List<String> issues;
    }
}
