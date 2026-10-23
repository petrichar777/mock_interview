package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class MistakeModuleStatsResponse {
    private Long moduleId;
    private String moduleName;
    private Integer mistakeCount;
    private Integer totalAttempts;
    private Integer correctAttempts;
    private Double correctRate;
}
