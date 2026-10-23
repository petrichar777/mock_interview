package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class PracticeModuleListResponse {

    private List<DirectionItem> directions;

    private List<CategoryItem> categories;

    private List<ModuleItem> list;

    private MistakeEntry mistakeEntry;

    @Data
    public static class DirectionItem {

        private Long id;

        private String code;

        private String name;

        private Boolean active;
    }

    @Data
    public static class CategoryItem {

        private Long directionId;

        private String code;

        private String name;

        private Boolean active;
    }

    @Data
    public static class ModuleItem {

        private Long id;

        private String categoryCode;

        private String categoryName;

        private String name;

        private String description;

        private List<String> questionTypes;

        private Integer totalQuestionCount;

        private Integer doneQuestionCount;

        private Integer progressPercent;

        private String ctaText;

        private String ctaType;
    }

    @Data
    public static class MistakeEntry {

        private Boolean enabled;

        private Integer mistakeCount;

        private String buttonText;
    }
}
