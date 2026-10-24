package com.campus.perks.assistant.entity;

import java.time.Instant;

public record MarketingDraftRow(
    String id,
    String merchantId,
    String createdBy,
    String requestKey,
    String inputHash,
    String brief,
    String tone,
    String couponId,
    String status,
    String title,
    String body,
    String sourceSnapshot,
    String sourceHash,
    int version,
    int attempt,
    String leaseToken,
    Instant lockedUntil,
    String errorCode,
    Instant createdAt,
    Instant updatedAt) {
  @Override
  public String toString() {
    return "MarketingDraftRow[REDACTED]";
  }
}
