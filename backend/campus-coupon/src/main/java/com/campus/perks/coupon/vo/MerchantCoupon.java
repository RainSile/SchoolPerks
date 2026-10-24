package com.campus.perks.coupon.vo;

import com.campus.perks.coupon.dto.CouponRules;
import java.time.Instant;

public record MerchantCoupon(
    String id,
    String merchantId,
    String title,
    String couponType,
    CouponRules rules,
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
    Instant publishedAt,
    String reviewReason) {}
