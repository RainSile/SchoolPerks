package com.campus.perks.analytics.service;

import java.time.*;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.analytics")
public record AnalyticsProperties(
    int defaultDays,
    int maxDays,
    String defaultZone,
    List<String> allowedZones,
    Duration cacheTtl,
    int maxCacheEntries,
    int topCoupons) {
  public AnalyticsProperties {
    if (defaultDays < 1
        || maxDays < defaultDays
        || maxDays > 366
        || allowedZones == null
        || !allowedZones.contains(defaultZone)
        || cacheTtl == null
        || cacheTtl.isNegative()
        || cacheTtl.compareTo(Duration.ofMinutes(5)) > 0
        || maxCacheEntries < 1
        || topCoupons < 1
        || topCoupons > 50) throw new IllegalArgumentException("Invalid analytics bounds");
    allowedZones.forEach(ZoneId::of);
    allowedZones = List.copyOf(allowedZones);
  }
}
