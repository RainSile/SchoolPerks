package com.campus.perks.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.integrations.amap")
public record AmapProperties(
    boolean enabled, String jsKey, String securityCode, Duration timeout, int requestsPerMinute) {
  @Override
  public String toString() {
    return "AmapProperties[REDACTED]";
  }
}
