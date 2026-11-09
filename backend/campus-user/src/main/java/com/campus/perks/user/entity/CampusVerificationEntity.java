package com.campus.perks.user.entity;

public record CampusVerificationEntity(
    String id,
    Long userId,
    Long schoolId,
    String schoolName,
    String status,
    java.time.Instant submittedAt,
    java.time.Instant reviewedAt,
    String reviewReason,
    String studentCardFileId,
    java.time.Instant evidenceDeletedAt) {
  @Override
  public String toString() {
    return "CampusVerificationEntity[REDACTED]";
  }
}
