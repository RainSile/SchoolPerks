package com.campus.perks.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "验证码挑战，不含验证码或手机号")
public record ChallengeView(String id, java.time.Instant expiresAt, String status) {
  @Override
  public String toString() {
    return "ChallengeView[REDACTED]";
  }
}
