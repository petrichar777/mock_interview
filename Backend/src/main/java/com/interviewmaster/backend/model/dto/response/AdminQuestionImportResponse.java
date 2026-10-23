package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class AdminQuestionImportResponse {

    private Integer total;

    private Integer successCount;

    private Integer failedCount;

    private List<FailedItem> failedItems;

    @Data
    public static class FailedItem {

        private Integer index;

        private String stem;

        private String reason;
    }
}
