package com.interviewmaster.backend.service;

import com.interviewmaster.backend.config.TrtcProperties;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.util.TLSSigAPIv2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrtcService {

    private static final String TRTC_USER_ID_PREFIX = "u";

    private final TrtcProperties trtcProperties;

    public String issueUserSig(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        validateConfiguration();
        try {
            TLSSigAPIv2 sigApi = new TLSSigAPIv2(
                    trtcProperties.getSdkAppId(), trtcProperties.getSecretKey());
            return sigApi.genUserSig(
                    TRTC_USER_ID_PREFIX + userId, trtcProperties.getUserSigExpireSeconds());
        } catch (IllegalArgumentException ex) {
            log.error("failed to issue TRTC user sig, userId={}, reason={}", userId, ex.getMessage());
            throw new BusinessException(500, "TRTC 凭证生成失败");
        }
    }

    public long getSdkAppId() {
        validateConfiguration();
        return trtcProperties.getSdkAppId();
    }

    public long getUserSigExpireSeconds() {
        validateConfiguration();
        return trtcProperties.getUserSigExpireSeconds();
    }

    private void validateConfiguration() {
        if (trtcProperties.getSdkAppId() <= 0
                || !StringUtils.hasText(trtcProperties.getSecretKey())
                || trtcProperties.getUserSigExpireSeconds() <= 0) {
            throw new BusinessException(500, "TRTC 未配置");
        }
    }
}
