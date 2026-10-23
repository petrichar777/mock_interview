package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class CurrentUserResponse {

    private Long id;

    private String username;

    private String email;

    private String role;

    private Integer status;

    private Integer continuousCheckinDays;

    private Integer doneQuestionCount;
}
