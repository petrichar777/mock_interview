package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class AdminQuestionDetailResponse {

    private Long id;

    private Long moduleId;

    private Integer type;

    private Integer difficulty;

    private String stem;

    private String analysis;

    private String tags;

    private Integer status;

    private List<OptionItem> options;

    @Data
    public static class OptionItem {

        private String key;

        private String text;

        private Boolean correct;

        private Integer sort;
    }
}
