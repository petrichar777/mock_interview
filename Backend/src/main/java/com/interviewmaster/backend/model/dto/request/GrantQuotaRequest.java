package com.interviewmaster.backend.model.dto.request;

import lombok.Data;

@Data
public class GrantQuotaRequest {

    private Integer interviewQuota;

    private Integer resumeQuota;
}
