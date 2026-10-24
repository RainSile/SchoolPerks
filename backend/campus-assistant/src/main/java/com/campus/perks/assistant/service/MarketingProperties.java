package com.campus.perks.assistant.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.assistant.marketing")
public record MarketingProperties(
    boolean enabled,
    int maxBriefLength,
    int maxTitleLength,
    int maxBodyLength,
    int requestsPerMinute,
    int maxAttempts,
    Duration leaseTtl) {
  public MarketingProperties {
    if (maxBriefLength < 1
        || maxBriefLength > 2000
        || maxTitleLength < 1
        || maxTitleLength > 160
        || maxBodyLength < 1
        || maxBodyLength > 10000
        || requestsPerMinute < 1
        || maxAttempts < 1
        || maxAttempts > 10
        || leaseTtl == null
        || leaseTtl.isNegative()
        || leaseTtl.isZero()) throw new IllegalArgumentException("Invalid marketing limits");
  }
}
