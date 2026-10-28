package com.campus.perks.integration.port;

import java.time.Duration;

/** Provider must enforce bounded network timeouts. Simulation is an explicit local-only adapter. */
public interface SmsSenderPort {
  record RemoteChallenge(
      String id,
      String phone,
      String purpose,
      int codeLength,
      Duration validity,
      Duration interval) {
    @Override
    public String toString() {
      return "RemoteChallenge[REDACTED]";
    }
  }

  record Receipt(String verifierType, String reference, String scope) {
    @Override
    public String toString() {
      return "SmsReceipt[REDACTED]";
    }
  }

  boolean available();

  void sendVerificationCode(String phone, String purpose, String code, Duration validity);

  default boolean verifiesRemotely() {
    return false;
  }

  default String verifierType() {
    return "LOCAL";
  }

  default Receipt startRemoteVerification(RemoteChallenge challenge) {
    throw new UnsupportedOperationException("Remote SMS verification is not supported");
  }

  default boolean verifyRemoteCode(
      String phone,
      String purpose,
      String scope,
      String reference,
      String challengeId,
      String code) {
    throw new UnsupportedOperationException("Remote SMS verification is not supported");
  }

  default boolean simulated() {
    return false;
  }

  /** Only a local simulation adapter may override generated codes. Never returned by HTTP. */
  default String codeOverride() {
    return null;
  }
}
