package com.campus.perks.user.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "campus.auth")
public record AuthProperties(
    boolean enabled,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    Duration resendInterval,
    Duration rateWindow,
    @Min(1) int phoneWindowLimit,
    @Min(1) int ipWindowLimit,
    @Min(1) @Max(10) int maxCodeAttempts,
    @Min(4) @Max(8) int codeLength,
    String codeHashSecretBase64,
    String identityKeyBase64,
    @NotBlank @Size(max = 58) String nicknamePrefix) {
  public AuthProperties {
    for (Duration duration :
        new Duration[] {accessTokenTtl, refreshTokenTtl, resendInterval, rateWindow}) {
      if (duration == null || duration.isNegative() || duration.isZero())
        throw new IllegalArgumentException("Authentication durations must be positive");
    }
    if (accessTokenTtl.compareTo(refreshTokenTtl) >= 0)
      throw new IllegalArgumentException("Access token TTL must be shorter than refresh TTL");
  }
}
