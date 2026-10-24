package com.campus.perks.coupon.entity;

import java.time.Instant;

public record RedemptionRate(Instant windowStart, int attempts) {}
