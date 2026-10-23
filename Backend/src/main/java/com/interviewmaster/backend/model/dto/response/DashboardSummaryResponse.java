package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class DashboardSummaryResponse {

    private Integer doneQuestionCount;

    private Double correctRate;

    private Integer interviewCount;

    private Integer totalPracticeTimeMinutes;

    private Integer continuousCheckinDays;

    private Integer remainingDailyInterviews;

    private Integer maxDailyInterviews;
}
