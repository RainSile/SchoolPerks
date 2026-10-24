package com.campus.perks.coupon.entity;

import java.time.Instant;

public record CouponRow(
    Long id,
    Long merchantId,
    String title,
    String couponType,
    String rules,
    int totalStock,
    int remainingStock,
    Instant validFrom,
    Instant validUntil,
    String status,
    int version,
    String description,
    boolean campusOnly,
    String reviewStatus,
    String reviewRequestId,
    Instant publishedAt) {}
