package com.interviewmaster.backend.model.dto.response;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RoomReportPayload {

    private Integer overallScore;

    private String summary;

    private List<ModuleMastery> moduleMastery = new ArrayList<>();

    private List<String> strengths = new ArrayList<>();

    private List<String> weaknesses = new ArrayList<>();

    private List<String> suggestions = new ArrayList<>();

    private List<String> uncoveredQuestions = new ArrayList<>();

    private List<RecommendedQuestion> recommendedQuestions = new ArrayList<>();

    @Data
    public static class ModuleMastery {

        private Long moduleId;

        private String moduleName;

        private String level;

        private String comment;
    }

    @Data
    public static class RecommendedQuestion {

        private Long questionId;

        private String questionStem;

        private String moduleName;
    }
}
