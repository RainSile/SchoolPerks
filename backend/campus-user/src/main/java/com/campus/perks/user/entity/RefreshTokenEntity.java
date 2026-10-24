package com.campus.perks.user.entity;

public record RefreshTokenEntity(
    String tokenHash, String sessionId, java.time.Instant expiresAt, java.time.Instant consumedAt) {
  @Override
  public String toString() {
    return "RefreshTokenEntity[REDACTED]";
  }
}
