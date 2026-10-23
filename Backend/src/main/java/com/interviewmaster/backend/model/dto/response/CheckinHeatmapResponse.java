package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class CheckinHeatmapResponse {

    private String month;

    private List<Integer> checkedDays;

    private Integer continuousDays;
}
