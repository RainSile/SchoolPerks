package com.campus.perks.integration.port;

import java.time.Instant;

/** Read-only public coupon facts, excluding claims, customers and private draft content. */
public interface PromotionFactsPort {
  record Coupon(
      String id,
      String merchantId,
      String title,
      String couponType,
      String rules,
      Instant validFrom,
      Instant validUntil,
      boolean campusOnly,
      String description) {}

  Coupon publicCoupon(String id);
}
