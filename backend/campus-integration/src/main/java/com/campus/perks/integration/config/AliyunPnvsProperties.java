package com.campus.perks.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.integrations.sms.aliyun-pnvs")
public record AliyunPnvsProperties(
    boolean enabled,
    String endpoint,
    String accessKeyId,
    String accessKeySecret,
    String signName,
    String countryCode,
    String loginTemplateCode,
    String registerTemplateCode,
    String resetTemplateCode,
    String loginScheme,
    String registerScheme,
    String resetScheme,
    Duration connectTimeout,
    Duration readTimeout) {
  @Override
  public String toString() {
    return "AliyunPnvsProperties[enabled=" + enabled + ", credentials=REDACTED]";
  }
}
