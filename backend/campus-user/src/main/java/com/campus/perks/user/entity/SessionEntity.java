package com.campus.perks.user.entity;

public record SessionEntity(
    String id,
    Long userId,
    java.time.Instant createdAt,
    java.time.Instant expiresAt,
    java.time.Instant revokedAt) {
  @Override
  public String toString() {
    return "SessionEntity[REDACTED]";
  }
}
