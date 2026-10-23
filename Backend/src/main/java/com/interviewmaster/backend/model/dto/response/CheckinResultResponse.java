package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class CheckinResultResponse {

    private Boolean checkedIn;

    private Integer continuousDays;
}
