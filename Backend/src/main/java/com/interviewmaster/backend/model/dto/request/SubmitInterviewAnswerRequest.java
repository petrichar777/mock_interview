package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitInterviewAnswerRequest {

    @NotBlank(message = "不能为空")
    @Size(max = 20000, message = "长度不能超过20000")
    private String content;

    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须大于等于1")
    private Integer turnNo;

    /**
     * 请求唯一标识，用于幂等性保护
     * 前端生成 UUID，相同 requestId 的重复请求会被拒绝
     */
    private String requestId;
}
