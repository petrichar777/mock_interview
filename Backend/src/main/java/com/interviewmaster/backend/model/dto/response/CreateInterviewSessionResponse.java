package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class CreateInterviewSessionResponse {

    private Long sessionId;

    private String sessionCode;

    private String title;

    private String mode;

    private String status;

    private Boolean quotaExhausted;

    private String welcomeText;

    private AlgoFlow algoFlow;

    private FirstMessage firstMessage;

    @Data
    public static class AlgoFlow {

        private Boolean enabled;

        private List<String> steps;

        private Integer currentStep;
    }

    @Data
    public static class FirstMessage {

        private Long messageId;

        private String role;

        private String kind;

        private Integer turnNo;

        private String content;

        private String createdAt;
    }
}
