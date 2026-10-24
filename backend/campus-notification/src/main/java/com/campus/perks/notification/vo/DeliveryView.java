package com.campus.perks.notification.vo;

import java.time.Instant;

public record DeliveryView(
    String id,
    String aggregateId,
    String type,
    String status,
    int attempts,
    Instant nextAttemptAt,
    Instant publishedAt,
    String lastErrorCode) {}
