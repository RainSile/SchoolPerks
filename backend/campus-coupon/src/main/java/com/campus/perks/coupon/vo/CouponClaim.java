package com.campus.perks.coupon.vo;

import com.campus.perks.coupon.dto.CouponRules;
import java.time.Instant;

public record CouponClaim(
    String id,
    String couponId,
    String merchantId,
    String merchantName,
    String title,
    String couponType,
    CouponRules rules,
    String status,
    Instant claimedAt,
    Instant validFrom,
    Instant validUntil,
    Instant redeemedAt,
    String redemptionId) {}
