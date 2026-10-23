package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryStatusRequest {

    @NotNull(message = "status 不能为空")
    private Integer status;
}
