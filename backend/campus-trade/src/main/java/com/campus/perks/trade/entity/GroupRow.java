package com.campus.perks.trade.entity;

import java.time.Instant;

public record GroupRow(
    long id,
    long offerId,
    long creatorId,
    int targetSize,
    int reservedCount,
    int paidCount,
    String status,
    Instant expiresAt) {}
