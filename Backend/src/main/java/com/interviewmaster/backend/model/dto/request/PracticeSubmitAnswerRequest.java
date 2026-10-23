package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class PracticeSubmitAnswerRequest {

    @Size(min = 1, message = "answers 至少包含一个选项")
    private List<String> answers;

    private String mode;

    private Long timeCostMs;
}
