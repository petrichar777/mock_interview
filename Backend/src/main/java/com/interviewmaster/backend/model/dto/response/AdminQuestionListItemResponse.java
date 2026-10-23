package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class AdminQuestionListItemResponse {

    private Long id;

    private String stem;

    private Integer type;

    private Integer difficulty;

    private Long moduleId;

    private String moduleName;

    private Integer status;

    private String updatedAt;
}
