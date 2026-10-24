package com.campus.perks.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "认证功能可用性")
public record AuthenticationCapabilities(
    boolean enabled,
    boolean smsAvailable,
    int codeLength,
    long resendAfterSeconds,
    boolean smsSimulated,
    int passwordMinLength,
    int passwordMaxBytes) {
  @Override
  public String toString() {
    return "AuthenticationCapabilities[REDACTED]";
  }
}
