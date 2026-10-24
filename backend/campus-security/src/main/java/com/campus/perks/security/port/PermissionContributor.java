package com.campus.perks.security.port;

import java.util.List;

/**
 * Domain memberships may contribute capabilities; data scope is still enforced in domain services.
 */
public interface PermissionContributor {
  List<String> permissions(String userId);
}
