package com.campus.perks.user.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("campus.auth.password")
public record PasswordProperties(
    @Min(10) @Max(16) int bcryptStrength,
    @Min(8) @Max(64) int minLength,
    @Min(8) @Max(72) int maxBytes,
    Duration rateWindow,
    @Min(1) int phoneLimit,
    @Min(1) int ipLimit) {
  public PasswordProperties {
    if (rateWindow == null
        || rateWindow.isNegative()
        || rateWindow.isZero()
        || maxBytes < minLength) throw new IllegalArgumentException("Invalid password policy");
  }
}
