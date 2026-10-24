package com.campus.perks.coupon.entity;

import java.time.Instant;

public record ReviewRequestRow(
    String id,
    Long couponId,
    Long submittedBy,
    int contentVersion,
    String contentSnapshot,
    String status,
    Instant createdAt,
    Instant reviewedAt) {}
