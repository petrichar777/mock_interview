package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class InterviewReportListResponse {

    private List<ReportItem> list;

    private Integer page;

    private Integer size;

    private Integer total;

    @Data
    public static class ReportItem {

        private Long sessionId;

        private String title;

        private String mode;

        private String style;

        private Integer totalScore;

        private String finishedAt;
    }
}
