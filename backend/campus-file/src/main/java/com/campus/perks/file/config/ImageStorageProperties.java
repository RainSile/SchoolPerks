package com.campus.perks.file.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "campus.images")
public record ImageStorageProperties(
    @NotBlank String studentCardPrefix,
    @NotBlank String avatarPrefix,
    @NotBlank String communityPrefix,
    @Min(1) long avatarMaxBytes) {
  public ImageStorageProperties {
    for (String prefix : java.util.List.of(studentCardPrefix, avatarPrefix, communityPrefix)) {
      if (!prefix.matches("[a-zA-Z0-9/_-]+") || prefix.contains("..") || prefix.startsWith("/") || prefix.endsWith("/"))
        throw new IllegalArgumentException("Image object prefixes must be relative directory paths");
    }
  }
}
