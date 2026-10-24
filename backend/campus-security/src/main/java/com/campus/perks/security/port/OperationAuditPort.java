package com.campus.perks.security.port;

import java.time.Instant;

public interface OperationAuditPort {
  record Event(
      String id,
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      String outcome,
      int httpStatus,
      String errorCode,
      long durationMillis,
      Instant createdAt) {}

  void record(Event event);
}
