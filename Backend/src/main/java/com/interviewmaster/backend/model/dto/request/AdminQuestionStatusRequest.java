package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminQuestionStatusRequest {

    @NotNull(message = "status 不能为空")
    private Integer status;
}
