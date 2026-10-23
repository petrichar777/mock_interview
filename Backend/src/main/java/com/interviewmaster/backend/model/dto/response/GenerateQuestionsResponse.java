package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionsResponse {

    private int generatedCount;
    private List<Long> questionIds;
}
