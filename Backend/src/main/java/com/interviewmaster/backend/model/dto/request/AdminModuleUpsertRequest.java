package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminModuleUpsertRequest {

    @NotNull(message = "categoryId 不能为空")
    private Long categoryId;

    @NotBlank(message = "name 不能为空")
    @Size(max = 128, message = "name 长度不能超过128")
    private String name;

    @Size(max = 1000, message = "description 长度不能超过1000")
    private String description;

    private Integer sort;

    private Integer status;
}
