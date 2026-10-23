package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class ReviewDraftQuestionsResponse {

    private int reviewed;
    private int published;
    private int deleted;
    private int pending;
    private List<ReviewDraftQuestionResponse> items;
}
