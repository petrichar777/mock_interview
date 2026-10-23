package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FinishInterviewRequest {

    @Size(max = 500, message = "长度不能超过500")
    private String reason;
}
