package com.campus.perks.coupon.vo;

import com.campus.perks.integration.contract.ReviewRecord;
import java.time.Instant;

public record CouponReviewRequest(
    String id,
    String couponId,
    String submittedBy,
    int contentVersion,
    String status,
    Instant createdAt,
    Instant reviewedAt,
    MerchantCoupon content,
    ReviewRecord review) {}
