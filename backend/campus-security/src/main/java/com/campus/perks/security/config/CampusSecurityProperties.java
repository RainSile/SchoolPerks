package com.campus.perks.security.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.security")
public record CampusSecurityProperties(boolean docsEnabled, List<String> allowedOrigins, Jwt jwt) {
  public record Jwt(boolean enabled, String secretBase64, String issuer, String audience) {}
}
