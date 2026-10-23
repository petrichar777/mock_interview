package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class FinishInterviewResponse {

    private Long sessionId;

    private String status;

    private String finishedAt;
}
