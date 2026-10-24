package com.campus.perks.notification.entity;

import java.time.Instant;

public record OutboxRow(
    String id,
    String aggregateId,
    String eventType,
    String payload,
    String status,
    int attempts,
    Instant nextAttemptAt,
    Instant createdAt,
    Instant publishedAt,
    String leaseToken,
    Instant lockedUntil,
    String lastErrorCode) {}
