package com.campus.perks.admin.entity;

import java.time.Instant;

public record AuditEventRow(
    String id,
    String actorId,
    String action,
    String resourceType,
    String resourceId,
    String details,
    Instant createdAt) {}
