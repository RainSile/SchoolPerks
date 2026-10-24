package com.campus.perks.integration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationConfiguration {
  public IntegrationConfiguration(IntegrationProperties properties) {
    if (properties.minio().enabled()) {
      throw new IllegalStateException("外部适配器尚未实现，请先完成 docs/HANDOFF.md 中的接入阶段再启用开关");
    }
  }
}
