package com.campus.perks.file.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "campus.files")
public record FileProperties(
    @Min(1) long maxBytes,
    @Min(1) int maxPendingPerUser,
    @NotEmpty Set<String> allowedTypes,
    Duration uploadTtl,
    Duration downloadTtl,
    @NotBlank @Size(max = 200) String objectPrefix) {
  public FileProperties {
    if (uploadTtl == null
        || downloadTtl == null
        || uploadTtl.toSeconds() < 1
        || downloadTtl.toSeconds() < 1)
      throw new IllegalArgumentException("File signing TTLs must be positive whole seconds");
    if (allowedTypes == null
        || !Set.of("application/pdf", "image/jpeg", "image/png").containsAll(allowedTypes))
      throw new IllegalArgumentException("File types must have an implemented signature validator");
  }
}
