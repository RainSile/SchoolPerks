package com.campus.perks.coupon.vo;

import java.time.Instant;

public record RedemptionView(
    String id, String claimId, String merchantId, String operatorId, Instant redeemedAt) {}
