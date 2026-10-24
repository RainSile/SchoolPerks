package com.campus.perks.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.infrastructure")
public record InfrastructureProperties(Redis redis, Mongo mongo, Rabbit rabbit) {
  public record Redis(boolean enabled, String uri, Duration timeout) {
    @Override
    public String toString() {
      return "Redis[REDACTED]";
    }
  }

  public record Mongo(boolean enabled, String uri, String database, Duration timeout) {
    @Override
    public String toString() {
      return "Mongo[REDACTED]";
    }
  }

  public record Rabbit(
      boolean enabled,
      String host,
      int port,
      String username,
      String password,
      String virtualHost,
      String queue,
      Duration timeout,
      int batchSize,
      int maxAttempts,
      int maxQueueLength,
      Duration retryDelay) {
    @Override
    public String toString() {
      return "Rabbit[REDACTED]";
    }
  }

  @Override
  public String toString() {
    return "InfrastructureProperties[REDACTED]";
  }
}
