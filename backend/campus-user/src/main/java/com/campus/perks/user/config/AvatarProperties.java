package com.campus.perks.user.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "campus.avatars")
public record AvatarProperties(@NotBlank String objectPrefix, @NotBlank String defaultKey,
    @NotEmpty Map<String,String> defaults, Duration downloadTtl) {
  public AvatarProperties {
    if (defaults == null || !defaults.containsKey(defaultKey)
        || defaults.keySet().stream().anyMatch(k -> !k.matches("[a-z0-9-]{1,64}"))
        || objectPrefix == null || !objectPrefix.matches("[a-zA-Z0-9/_-]+")
        || objectPrefix.startsWith("/") || objectPrefix.endsWith("/") || objectPrefix.contains("..")
        || downloadTtl == null || downloadTtl.isNegative() || downloadTtl.isZero())
      throw new IllegalArgumentException("Invalid avatar catalog configuration");
  }
}
