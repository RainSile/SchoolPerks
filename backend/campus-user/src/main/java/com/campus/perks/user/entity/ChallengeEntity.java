package com.campus.perks.user.entity;

public record ChallengeEntity(
    String id,
    String phone,
    String purpose,
    String codeHash,
    int attempts,
    String status,
    java.time.Instant createdAt,
    java.time.Instant expiresAt) {
  @Override
  public String toString() {
    return "ChallengeEntity[REDACTED]";
  }
}
