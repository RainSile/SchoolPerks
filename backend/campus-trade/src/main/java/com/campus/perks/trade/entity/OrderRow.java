package com.campus.perks.trade.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderRow(
    long id,
    String orderNo,
    long userId,
    long merchantId,
    long offerId,
    Long groupId,
    BigDecimal amount,
    String snapshot,
    String status,
    Instant expiresAt,
    Instant paidAt,
    boolean stockReleased,
    Instant createdAt) {}
