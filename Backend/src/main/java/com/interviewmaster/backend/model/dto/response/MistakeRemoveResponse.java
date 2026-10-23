package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class MistakeRemoveResponse {

    private Boolean removed;

    private Integer mistakeCount;
}
