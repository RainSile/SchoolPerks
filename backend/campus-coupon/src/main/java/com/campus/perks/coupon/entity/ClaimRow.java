package com.campus.perks.coupon.entity;

import java.time.Instant;

public record ClaimRow(
    Long id,
    Long couponId,
    Long userId,
    String redemptionCodeHash,
    String status,
    Instant claimedAt,
    Instant redeemedAt,
    Instant codeExpiresAt,
    Instant codeIssuedAt) {
  @Override
  public String toString() {
    return "ClaimRow[REDACTED]";
  }
}
