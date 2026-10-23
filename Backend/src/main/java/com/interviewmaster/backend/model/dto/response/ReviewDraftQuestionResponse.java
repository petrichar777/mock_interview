package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class ReviewDraftQuestionResponse {

    private Long questionId;
    private String stem;
    private boolean correct;
    private int confidence;
    private List<String> issues;
    private String action;
}
