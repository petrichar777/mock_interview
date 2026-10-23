package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class ResumeOptimizeResponse {

    private Integer overallScore;

    private String summary;

    private List<ResumeDimension> dimensions;

    private String jdMatch;

    private List<SectionAdvice> sectionAdvice;

    private List<String> interviewInsights;

    @Data
    public static class ResumeDimension {

        private String name;

        private Integer score;

        private Integer maxScore;

        private String comment;
    }

    @Data
    public static class SectionAdvice {

        private String sectionName;

        private String originalSummary;

        private String suggestion;
    }
}
