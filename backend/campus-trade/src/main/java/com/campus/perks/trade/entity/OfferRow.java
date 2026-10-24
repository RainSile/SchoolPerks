package com.campus.perks.trade.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record OfferRow(
    long id,
    long merchantId,
    String title,
    String description,
    String kind,
    BigDecimal price,
    int stockTotal,
    int stockAvailable,
    Instant startsAt,
    Instant endsAt,
    int groupSize,
    int groupDurationSeconds,
    String status,
    int version) {}
