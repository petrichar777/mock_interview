package com.interviewmaster.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean globalFallbackEnabled = true;

    @Min(1)
    private int globalFallbackCount = 300;

    @Min(1)
    private int globalFallbackPeriod = 60;

    @NotBlank
    private String globalFallbackMessage = "请求过于频繁，请稍后再试";
}
