package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class AdminUserListItemResponse {

    private Long id;

    private String username;

    private String email;

    private String role;

    private Integer status;

    private String createdAt;

    private Integer extraInterviewQuota;

    private Integer extraResumeQuota;
}
