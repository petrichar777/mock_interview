package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginByCodeRequest {

    @NotBlank(message = "不能为空")
    @Email(message = "格式不正确")
    @Size(max = 128, message = "长度不能超过128")
    private String email;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 6, message = "验证码为6位数字")
    private String code;
}
