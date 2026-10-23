package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class MistakeStatsResponse {

    private Integer todayResolved;

    private Integer consecutiveReviewDays;

    private Integer weeklyResolved;

    private Integer totalMistakes;
}
