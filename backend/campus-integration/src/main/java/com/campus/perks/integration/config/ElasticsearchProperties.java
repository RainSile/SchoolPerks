package com.campus.perks.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.search.elasticsearch")
public record ElasticsearchProperties(
    String endpoint,
    String apiKey,
    String index,
    String expectedVersion,
    String ikVersion,
    Duration timeout) {
  @Override
  public String toString() {
    return "ElasticsearchProperties[REDACTED]";
  }
}
