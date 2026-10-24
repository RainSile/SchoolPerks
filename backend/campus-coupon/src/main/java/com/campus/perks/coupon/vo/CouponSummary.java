package com.campus.perks.coupon.vo;

import com.campus.perks.coupon.dto.CouponRules;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "公开优惠；不含核销码或私人信息")
public record CouponSummary(
    String id,
    String merchantId,
    String merchantName,
    String schoolId,
    String title,
    String couponType,
    CouponRules rules,
    int remainingStock,
    Instant validFrom,
    Instant validUntil,
    boolean campusOnly,
    String description) {}
