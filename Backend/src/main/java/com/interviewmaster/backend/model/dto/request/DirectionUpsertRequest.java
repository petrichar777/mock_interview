package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DirectionUpsertRequest {

    @NotBlank(message = "code 不能为空")
    @Size(max = 32, message = "code 长度不能超过32")
    private String code;

    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过64")
    private String name;

    private Integer sort;

    private Integer status;
}
