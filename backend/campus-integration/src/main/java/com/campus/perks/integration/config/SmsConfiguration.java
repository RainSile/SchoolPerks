package com.campus.perks.integration.config;

import com.campus.perks.integration.adapter.AliyunPnvsSmsAdapter;
import com.campus.perks.integration.port.SmsSenderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(AliyunPnvsProperties.class)
public class SmsConfiguration {
  @Bean
  @ConditionalOnMissingBean(SmsSenderPort.class)
  @ConditionalOnProperty(name = "campus.integrations.sms.aliyun-pnvs.enabled", havingValue = "true")
  SmsSenderPort aliyunPnvsSmsSender(
      AliyunPnvsProperties properties, ObjectMapper json, Environment environment) {
    if (environment.getProperty("campus.integrations.sms.simulated-enabled", Boolean.class, false))
      throw new IllegalStateException("Real and simulated SMS cannot both be enabled");
    return new AliyunPnvsSmsAdapter(properties, json);
  }

  @Bean
  @ConditionalOnMissingBean(SmsSenderPort.class)
  @ConditionalOnProperty(
      name = "campus.integrations.sms.aliyun-pnvs.enabled",
      havingValue = "false",
      matchIfMissing = true)
  SmsSenderPort unavailableSmsSender(Environment environment) {
    if (environment.getProperty(
        "campus.integrations.sms.simulated-enabled", Boolean.class, false)) {
      if (!environment.matchesProfiles("local")
          || environment.matchesProfiles("prod", "production"))
        throw new IllegalStateException(
            "Simulated SMS requires local profile and is forbidden in production");
      String code = environment.getProperty("campus.integrations.sms.simulated-code", "");
      if (!code.matches("[0-9]{4,8}"))
        throw new IllegalStateException("Configure a 4-8 digit local SMS simulation code");
      return new SmsSenderPort() {
        public boolean available() {
          return true;
        }

        public boolean simulated() {
          return true;
        }

        public String codeOverride() {
          return code;
        }

        public void sendVerificationCode(
            String phone, String purpose, String value, Duration validity) {
          // Explicit local simulation: no network call, no secret or phone logging.
        }
      };
    }
    return new SmsSenderPort() {
      public boolean available() {
        return false;
      }

      public void sendVerificationCode(
          String phone, String purpose, String code, Duration validity) {
        throw new IllegalStateException("SMS provider is not configured");
      }
    };
  }
}
