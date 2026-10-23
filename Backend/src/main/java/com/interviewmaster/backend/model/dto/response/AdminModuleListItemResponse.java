package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class AdminModuleListItemResponse {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String categoryCode;

    private Long directionId;

    private String directionName;

    private String directionCode;

    private String name;

    private Integer status;

    private Integer sort;

    private Integer questionCount;
}
