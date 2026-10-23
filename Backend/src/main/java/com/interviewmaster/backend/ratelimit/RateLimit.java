package com.interviewmaster.backend.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流声明。方法上未声明时，回退读取类上的配置。
 * 多个维度或窗口可重复标注。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimit.List.class)
public @interface RateLimit {

    String key() default "";

    int count() default 60;

    /**
     * 窗口长度，单位秒。
     */
    int period() default 60;

    RateLimitDimension dimension() default RateLimitDimension.USER;

    String message() default "请求过于频繁，请稍后再试";

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        RateLimit[] value();
    }
}
