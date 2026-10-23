package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class ResumeOptimizeResultResponse {

    private String status;
    private ResumeOptimizeResponse result;
    private String errorMessage;
    private String resumeText;
    private String jobDescription;
}
