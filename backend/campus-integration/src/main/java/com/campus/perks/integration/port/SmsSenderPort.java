package com.campus.perks.integration.port;

import java.time.Duration;

/** Provider must enforce bounded network timeouts. Simulation is an explicit local-only adapter. */
public interface SmsSenderPort {
  boolean available();

  void sendVerificationCode(String phone, String purpose, String code, Duration validity);

  default boolean simulated() {
    return false;
  }

  /** Only a local simulation adapter may override generated codes. Never returned by HTTP. */
  default String codeOverride() {
    return null;
  }
}
