package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class InterviewSessionMessageResponse {

    private Long id;

    private String role;

    private String kind;

    private Integer turnNo;

    private String content;

    private String contentFormat;

    private String reviewTitle;

    private String reviewType;

    private Boolean typing;

    private String createdAt;
}
