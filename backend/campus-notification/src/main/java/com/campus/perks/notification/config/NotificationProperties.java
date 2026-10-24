package com.campus.perks.notification.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "campus.notifications")
public record NotificationProperties(
    @Min(100) long pollIntervalMs,
    @Min(1) int batchSize,
    @Min(1) int maxAttempts,
    Duration leaseTtl,
    Duration retryBase,
    Duration retryMax,
    Duration authenticationTimeout,
    @Min(1) int maxConnections,
    @Min(1) int maxConnectionsPerUser,
    @Min(256) int maxMessageBytes,
    @Min(100) int sendTimeLimitMs,
    @Min(1024) int sendBufferBytes,
    @Min(1) int pingIntervalSeconds,
    @Min(1) int reconnectDelaySeconds) {
  public NotificationProperties {
    for (Duration d : new Duration[] {leaseTtl, retryBase, retryMax, authenticationTimeout})
      if (d == null || d.toMillis() < 1)
        throw new IllegalArgumentException("Notification durations must be positive");
    if (retryMax.compareTo(retryBase) < 0)
      throw new IllegalArgumentException("Invalid retry interval");
  }
}
