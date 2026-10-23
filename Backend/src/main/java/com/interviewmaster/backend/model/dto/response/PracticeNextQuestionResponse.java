package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class PracticeNextQuestionResponse {

    private Long questionId;

    private Long moduleId;

    private String moduleName;

    private String mode;

    private Integer type;

    private String typeText;

    private Integer difficulty;

    private String difficultyText;

    private String stem;

    private List<OptionItem> options;

    private Integer progressCurrent;

    private Integer progressTotal;

    private Integer progressPercent;

    private Boolean hasPrev;

    private Boolean hasNext;

    @Data
    public static class OptionItem {

        private String key;

        private String text;
    }
}
