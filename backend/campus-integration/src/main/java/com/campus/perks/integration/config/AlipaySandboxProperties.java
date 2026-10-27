package com.campus.perks.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.integrations.alipay-sandbox")
public record AlipaySandboxProperties(
    boolean enabled,
    String gateway,
    String appId,
    String sellerId,
    String privateKey,
    String publicKey,
    String notifyUrl,
    Duration connectTimeout,
    Duration readTimeout) {
  @Override
  public String toString() {
    return "AlipaySandboxProperties[enabled=" + enabled + ", credentials=REDACTED]";
  }
}
