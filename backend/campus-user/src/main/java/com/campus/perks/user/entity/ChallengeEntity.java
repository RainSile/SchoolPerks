package com.campus.perks.user.entity;

public record ChallengeEntity(
    String id,
    String phone,
    String purpose,
    String codeHash,
    int attempts,
    String status,
    java.time.Instant createdAt,
    java.time.Instant expiresAt,
    String verifierType,
    String providerReference,
    String providerScope) {
  @org.apache.ibatis.annotations.AutomapConstructor
  public ChallengeEntity {}

  public ChallengeEntity(
      String id,
      String phone,
      String purpose,
      String codeHash,
      int attempts,
      String status,
      java.time.Instant createdAt,
      java.time.Instant expiresAt) {
    this(id, phone, purpose, codeHash, attempts, status, createdAt, expiresAt, "LOCAL", null, null);
  }

  @Override
  public String toString() {
    return "ChallengeEntity[REDACTED]";
  }
}
