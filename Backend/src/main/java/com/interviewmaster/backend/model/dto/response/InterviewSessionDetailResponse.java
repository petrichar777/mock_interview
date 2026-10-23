package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class InterviewSessionDetailResponse {

    private Long sessionId;

    private String sessionCode;

    private String title;

    private String mode;

    private String style;

    private String status;

    private String welcomeText;

    private AlgoFlow algoFlow;

    private String startedAt;

    private String finishedAt;

    @Data
    public static class AlgoFlow {

        private Boolean enabled;

        private List<String> steps;

        private Integer currentStep;
    }
}
