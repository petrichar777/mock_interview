package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class SubmitInterviewAnswerResponse {

    private AssistantMessage assistantMessage;

    private AlgoFlow algoFlow;

    private Boolean isFinished;

    @Data
    public static class AssistantMessage {

        private Long id;

        private String role;

        private String kind;

        private Integer turnNo;

        private String content;

        private String contentFormat;

        private Boolean typing;

        private String createdAt;
    }

    @Data
    public static class AlgoFlow {

        private Boolean enabled;

        private List<String> steps;

        private Integer currentStep;
    }

}
