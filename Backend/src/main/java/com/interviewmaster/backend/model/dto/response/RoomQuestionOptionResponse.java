package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class RoomQuestionOptionResponse {

    private Long id;

    private String stem;

    private Long moduleId;

    private String moduleName;

    private Integer difficulty;
}
