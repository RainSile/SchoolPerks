package com.campus.perks.search.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.search")
public record SearchProperties(
    String backend, int maxWindow, int excerptLength, int batchSize, Duration leaseTtl) {
  public SearchProperties {
    if (!java.util.Set.of("MYSQL", "ELASTICSEARCH").contains(backend)
        || maxWindow < 1
        || maxWindow > 1000
        || excerptLength < 20
        || excerptLength > 1000
        || batchSize < 1
        || batchSize > 100
        || leaseTtl == null
        || leaseTtl.isZero()
        || leaseTtl.isNegative())
      throw new IllegalArgumentException("Invalid search configuration");
  }
}
