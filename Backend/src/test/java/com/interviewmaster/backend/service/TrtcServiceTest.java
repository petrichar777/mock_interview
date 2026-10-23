package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.interviewmaster.backend.config.TrtcProperties;
import com.interviewmaster.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

class TrtcServiceTest {

    @Test
    void issueUserSig_notConfigured_throwsBusinessException() {
        TrtcProperties properties = new TrtcProperties();
        TrtcService trtcService = new TrtcService(properties);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trtcService.issueUserSig(1L));

        assertEquals(500, ex.getCode());
    }

    @Test
    void issueUserSig_nullUser_throwsUnauthorized() {
        TrtcService trtcService = new TrtcService(configuredProperties());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trtcService.issueUserSig(null));

        assertEquals(401, ex.getCode());
    }

    @Test
    void issueUserSig_configured_returnsSignature() {
        TrtcService trtcService = new TrtcService(configuredProperties());

        String userSig = trtcService.issueUserSig(12L);

        assertNotNull(userSig);
        assertFalse(userSig.isBlank());
        assertFalse(userSig.contains("+"));
        assertFalse(userSig.contains("/"));
    }

    @Test
    void getSdkAppId_configured_returnsValue() {
        TrtcService trtcService = new TrtcService(configuredProperties());

        assertEquals(1400000000L, trtcService.getSdkAppId());
    }

    private TrtcProperties configuredProperties() {
        TrtcProperties properties = new TrtcProperties();
        properties.setSdkAppId(1400000000L);
        properties.setSecretKey("test-secret");
        properties.setUserSigExpireSeconds(7200L);
        return properties;
    }
}
