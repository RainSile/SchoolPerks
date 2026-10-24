package com.campus.perks.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录或刷新接口专用敏感响应，禁止记录")
public record SessionTokens(
    String sessionId,
    String tokenType,
    String accessToken,
    java.time.Instant accessExpiresAt,
    String refreshToken,
    java.time.Instant refreshExpiresAt) {
  @Override
  public String toString() {
    return "SessionTokens[REDACTED]";
  }
}
