package com.campus.perks.file.entity;

import java.time.Instant;

public record FileObject(
    String id,
    Long ownerId,
    String purpose,
    String originalName,
    String contentType,
    long declaredSize,
    String stagingKey,
    String immutableKey,
    String status,
    Instant createdAt,
    Instant expiresAt,
    Instant completedAt) {
  @Override
  public String toString() {
    return "FileObject[REDACTED]";
  }
}
