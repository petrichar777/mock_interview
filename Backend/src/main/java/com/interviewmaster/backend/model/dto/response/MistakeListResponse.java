package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class MistakeListResponse {

    private List<Item> list;

    private Integer page;

    private Integer size;

    private Integer total;

    @Data
    public static class Item {

        private Long questionId;

        private Long moduleId;

        private String moduleName;

        private Integer type;

        private Integer difficulty;

        private String stem;

        private String lastWrongAt;
    }
}
