package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class PracticeAddMistakeResponse {

    private Boolean added;

    private Integer mistakeCount;
}
