package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "不能为空")
    @Email(message = "格式不正确")
    @Size(max = 128, message = "长度不能超过128")
    private String email;

    @NotBlank(message = "不能为空")
    @Size(max = 16, message = "长度不能超过16")
    private String verifyCode;

    @NotBlank(message = "不能为空")
    @Size(min = 8, max = 64, message = "长度必须在8-64之间")
    private String newPassword;
}
