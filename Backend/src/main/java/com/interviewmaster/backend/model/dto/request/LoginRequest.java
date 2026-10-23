package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "不能为空")
    @Size(max = 128, message = "长度不能超过128")
    private String account;

    @NotBlank(message = "不能为空")
    @Size(min = 8, max = 64, message = "长度必须在8-64之间")
    private String password;
}
