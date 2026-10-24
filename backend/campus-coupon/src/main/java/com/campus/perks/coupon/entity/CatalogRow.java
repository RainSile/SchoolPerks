package com.campus.perks.coupon.entity;

import java.time.Instant;

public record CatalogRow(
    Long id,
    Long merchantId,
    String merchantName,
    Long schoolId,
    String title,
    String couponType,
    String rules,
    int remainingStock,
    Instant validFrom,
    Instant validUntil,
    boolean campusOnly,
    String description) {}
