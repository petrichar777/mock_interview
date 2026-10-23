package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class PracticeSubmitAnswerResponse {

    private Boolean correct;

    private List<String> correctAnswers;

    private List<String> explanations;

    private List<String> tags;

    private Boolean autoAddedToMistakes;

    private Boolean autoRemovedFromMistakes;

    private Boolean hasNext;

    private Boolean finished;
}
