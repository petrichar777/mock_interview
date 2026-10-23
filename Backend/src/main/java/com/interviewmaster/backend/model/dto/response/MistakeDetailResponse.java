package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class MistakeDetailResponse {

    private Long questionId;

    private Long moduleId;

    private String moduleName;

    private Integer type;

    private Integer difficulty;

    private String stem;

    private List<OptionItem> options;

    private List<String> correctAnswers;

    private List<String> explanations;

    private List<String> tags;

    private String lastWrongAt;

    private List<String> lastWrongAnswers;

    @Data
    public static class OptionItem {

        private String key;

        private String text;
    }
}
