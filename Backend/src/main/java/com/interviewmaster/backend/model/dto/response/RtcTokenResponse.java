package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class RtcTokenResponse {

    private Long sdkAppId;

    private String userId;

    private String userSig;

    private Long expireSeconds;

    private Long roomId;
}
