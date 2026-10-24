package com.campus.perks.integration.port;

import java.util.Map;

/** Implemented by admin. Uses the caller's transaction; failure must roll back business changes. */
public interface AuditPort {
  String record(
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      Map<String, Object> details);
}
