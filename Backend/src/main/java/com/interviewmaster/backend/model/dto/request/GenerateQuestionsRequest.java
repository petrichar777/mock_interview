package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateQuestionsRequest {

    @NotNull(message = "moduleId 不能为空")
    private Long moduleId;

    @Min(1)
    @Max(20)
    private Integer count = 5;

    private Integer difficulty;

    private Integer type;
}
