package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class QuotaInfoResponse {

    private int remainingResumeOptimizations;
    private int maxResumeOptimizations;
}
