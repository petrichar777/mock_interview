package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class DashboardRecommendedPracticesResponse {

    private List<Item> list;

    @Data
    public static class Item {

        private Long moduleId;

        private String moduleName;

        private String description;

        private String ctaText;

        private String ctaType;

        private String categoryCode;
    }
}
