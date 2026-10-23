package com.interviewmaster.backend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("im_rate_limit_log")
public class RateLimitLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String limitKey;

    private String dimension;

    private Long userId;

    private String clientIp;

    private String requestUri;

    private String requestMethod;

    private Integer limitCount;

    private Integer windowSeconds;

    private Integer currentCount;

    private LocalDateTime createdAt;
}
