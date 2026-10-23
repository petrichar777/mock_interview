package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class RoomQuestionResponse {

    private Long id;

    private Long questionId;

    private String questionStem;

    private Long moduleId;

    private String moduleName;

    private Integer orderNo;

    private Integer result;

    private String note;
}
