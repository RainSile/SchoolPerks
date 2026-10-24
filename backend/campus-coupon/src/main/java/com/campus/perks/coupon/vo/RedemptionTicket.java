package com.campus.perks.coupon.vo;

import java.time.Instant;

public record RedemptionTicket(String code, String qrCodeSvg, Instant expiresAt) {
  @Override
  public String toString() {
    return "RedemptionTicket[REDACTED]";
  }
}
