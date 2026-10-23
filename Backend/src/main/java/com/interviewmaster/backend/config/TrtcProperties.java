package com.interviewmaster.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "trtc")
public class TrtcProperties {

    private long sdkAppId;

    private String secretKey;

    private long userSigExpireSeconds = 7200L;
}
