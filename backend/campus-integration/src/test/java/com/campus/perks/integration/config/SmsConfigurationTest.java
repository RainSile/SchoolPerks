package com.campus.perks.integration.config;

import static org.assertj.core.api.Assertions.*;

import com.campus.perks.integration.port.SmsSenderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private ApplicationContextRunner configuredPnvs() {
    return runner
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withPropertyValues(
            "campus.integrations.sms.aliyun-pnvs.enabled=true",
            "campus.integrations.sms.aliyun-pnvs.endpoint=https://dypnsapi.aliyuncs.com",
            "campus.integrations.sms.aliyun-pnvs.access-key-id=fixture-id",
            "campus.integrations.sms.aliyun-pnvs.access-key-secret=fixture-secret",
            "campus.integrations.sms.aliyun-pnvs.sign-name=测试签名",
            "campus.integrations.sms.aliyun-pnvs.country-code=86",
            "campus.integrations.sms.aliyun-pnvs.login-template-code=100001",
            "campus.integrations.sms.aliyun-pnvs.register-template-code=100001",
            "campus.integrations.sms.aliyun-pnvs.reset-template-code=100003",
            "campus.integrations.sms.aliyun-pnvs.login-scheme=campus-login",
            "campus.integrations.sms.aliyun-pnvs.register-scheme=campus-register",
            "campus.integrations.sms.aliyun-pnvs.reset-scheme=campus-reset",
            "campus.integrations.sms.aliyun-pnvs.connect-timeout=2s",
            "campus.integrations.sms.aliyun-pnvs.read-timeout=3s");
  }

  @Test
  void realProviderHasOneBeanAndRequiresDistinctPurposes() {
    configuredPnvs()
        .run(
            c -> {
              assertThat(c).hasNotFailed().hasSingleBean(SmsSenderPort.class);
              assertThat(c.getBean(SmsSenderPort.class).verifiesRemotely()).isTrue();
            });
    configuredPnvs()
        .withPropertyValues("campus.integrations.sms.aliyun-pnvs.register-scheme=campus-login")
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void realProviderRejectsSimulationAndInsecureEndpoints() {
    configuredPnvs()
        .withPropertyValues("campus.integrations.sms.simulated-enabled=true")
        .run(c -> assertThat(c).hasFailed());
    configuredPnvs()
        .withPropertyValues(
            "campus.integrations.sms.aliyun-pnvs.endpoint=http://dypnsapi.aliyuncs.com")
        .run(c -> assertThat(c).hasFailed());
  }
}
