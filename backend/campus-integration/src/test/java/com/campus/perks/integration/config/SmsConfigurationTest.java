package com.campus.perks.integration.config;

import static org.assertj.core.api.Assertions.*;

import com.campus.perks.integration.port.SmsSenderPort;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SmsConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(SmsConfiguration.class);

  @Test
  void absentProviderIsUnavailableByDefault() {
    runner.run(
        c -> {
          assertThat(c).hasNotFailed();
          assertThat(c.getBean(SmsSenderPort.class).available()).isFalse();
        });
  }

  @Test
  void simulationUsesOnlyConfiguredCodeAndNeedsNoNetwork() {
    runner
        .withPropertyValues(
            "spring.profiles.active=local",
            "campus.integrations.sms.simulated-enabled=true",
            "campus.integrations.sms.simulated-code=8752")
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              var sms = c.getBean(SmsSenderPort.class);
              assertThat(sms.available()).isTrue();
              assertThat(sms.simulated()).isTrue();
              assertThat(sms.codeOverride()).isEqualTo("8752");
              sms.sendVerificationCode("13800000000", "REGISTER", "8752", Duration.ofMinutes(5));
            });
  }

  @Test
  void simulationFailsWithoutExplicitCodeOrOutsideLocalProfile() {
    runner
        .withPropertyValues(
            "spring.profiles.active=local", "campus.integrations.sms.simulated-enabled=true")
        .run(c -> assertThat(c).hasFailed());
    runner
        .withPropertyValues(
            "campus.integrations.sms.simulated-enabled=true",
            "campus.integrations.sms.simulated-code=8752")
        .run(c -> assertThat(c).hasFailed());
    runner
        .withPropertyValues(
            "spring.profiles.active=local,prod",
            "campus.integrations.sms.simulated-enabled=true",
            "campus.integrations.sms.simulated-code=8752")
        .run(c -> assertThat(c).hasFailed());
    runner
        .withPropertyValues(
            "spring.profiles.active=production",
            "campus.integrations.sms.simulated-enabled=true",
            "campus.integrations.sms.simulated-code=8752")
        .run(c -> assertThat(c).hasFailed());
  }
}
