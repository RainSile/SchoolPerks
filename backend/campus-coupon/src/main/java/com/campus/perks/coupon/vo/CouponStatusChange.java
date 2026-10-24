package com.campus.perks.coupon.vo;

import java.time.Instant;

public record CouponStatusChange(
    String id,
    String couponId,
    String actorId,
    String previousStatus,
    String status,
    int version,
    String reason,
    Instant createdAt) {}
