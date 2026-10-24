package com.campus.perks.notification.entity;

import java.time.Instant;

public record NotificationRow(
    String id,
    String eventId,
    Long userId,
    String eventType,
    String payload,
    Instant createdAt,
    Instant readAt) {}
