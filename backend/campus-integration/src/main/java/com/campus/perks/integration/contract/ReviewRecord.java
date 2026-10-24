package com.campus.perks.integration.contract;

import java.time.Instant;

public record ReviewRecord(
    String id,
    String resourceId,
    String reviewerId,
    String decision,
    String reason,
    Instant createdAt) {}
