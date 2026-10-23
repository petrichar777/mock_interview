package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class EvaluateInterviewResponse {

    private Integer totalScore;

    private String summary;

    private List<DimensionScore> dimensions;

    private String highlights;

    private String improvements;

    private String suggestion;

    private String resumeAdvice;

    @Data
    public static class DimensionScore {

        private String name;

        private Integer score;

        private Integer maxScore;

        private String comment;
    }
}
