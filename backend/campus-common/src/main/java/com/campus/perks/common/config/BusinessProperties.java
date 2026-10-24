package com.campus.perks.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 业务规则默认值位于配置文件，后续实现应注入配置而非在 Service 中写死。 */
@ConfigurationProperties(prefix = "campus.business")
public record BusinessProperties(
    Duration verificationCodeTtl, Duration orderPaymentTimeout, int couponClaimLimit) {}
