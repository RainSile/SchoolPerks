package com.campus.perks.user.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AuthenticationConfiguration {
  public AuthenticationConfiguration(Environment env) {
    if (!env.getProperty("campus.database.enabled", Boolean.class, false)
        || !env.getProperty("campus.security.jwt.enabled", Boolean.class, false))
      throw new IllegalStateException(
          "AUTH_ENABLED requires a database profile and JWT_ENABLED=true");
  }
}
