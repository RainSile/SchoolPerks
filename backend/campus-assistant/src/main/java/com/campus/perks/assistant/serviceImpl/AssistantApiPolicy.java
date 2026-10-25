package com.campus.perks.assistant.serviceImpl;

import com.campus.perks.security.service.*;

/**
 * Assistant boundaries are narrower than public HTTP availability. Shared account/file APIs remain
 * scoped to the authenticated subject. Admin expansion needs an actual DB-backed grant.
 */
public final class AssistantApiPolicy {
  private AssistantApiPolicy() {}

  public static String portal(String path, String method) {
    if (path.startsWith("/api/v1/admin/")) return "ADMIN";
    if (method.equals("GET")
        && (path.equals("/api/v1/merchants") || path.matches("/api/v1/merchants/\\{[^}]+}")))
      return "USER";
    if (path.startsWith("/api/v1/merchants/")
        || path.startsWith("/api/v1/merchant/")
        || path.equals("/api/v1/users/me/merchants")
        || path.startsWith("/api/v1/redemptions")
        || path.startsWith("/api/v1/coupons/")
            && (method.equals("PATCH")
                || path.contains("/management")
                || path.contains("/status-changes")
                || path.contains("/review-requests"))
        || path.startsWith("/api/v1/trade-offers/") && method.equals("PATCH")
        || path.startsWith("/api/v1/coupon-review-requests")
        || path.startsWith("/api/v1/marketing-drafts")) return "MERCHANT";
    if (path.startsWith("/api/v1/users/me/posts")
        || path.startsWith("/api/v1/users/me/post-comments")
        || path.startsWith("/api/v1/users/me/coupon-claims")
        || path.startsWith("/api/v1/users/me/campus-verifications")
        || path.startsWith("/api/v1/users/me/merchant-applications")
        || path.startsWith("/api/v1/merchant-applications")
        || path.startsWith("/api/v1/orders")
        || path.startsWith("/api/v1/group-purchases")
        || path.startsWith("/api/v1/coupon")
        || path.startsWith("/api/v1/trade-offers")
        || path.startsWith("/api/v1/posts")
        || path.startsWith("/api/v1/post-comments")
        || path.startsWith("/api/v1/post-reports")
        || path.startsWith("/api/v1/post-review-requests")
        || path.equals("/api/v1/search-results")
        || path.equals("/api/v1/nearby-merchants")) return "USER";
    if (java.util.Set.of(
                "/api/v1/community-capabilities",
                "/api/v1/map-capabilities",
                "/api/v1/merchant-categories",
                "/api/v1/notification-capabilities",
                "/api/v1/platform",
                "/api/v1/schools",
                "/api/v1/search-capabilities",
                "/api/v1/storage-capabilities",
                "/api/v1/trade-capabilities",
                "/api/v1/uploads",
                "/api/v1/users/me")
            .contains(path)
        || path.startsWith("/api/v1/uploads/")
        || path.startsWith("/api/v1/users/me/notifications")) return "SHARED";
    return "UNCLASSIFIED";
  }

  public static String permission(String path) {
    if (path.startsWith("/api/v1/admin/roles")
        || path.startsWith("/api/v1/admin/accounts")
        || path.equals("/api/v1/admin/permissions")) return "rbac:manage";
    if (path.startsWith("/api/v1/admin/merchant")) return "merchant:review";
    if (path.startsWith("/api/v1/admin/campus")) return "campus:review";
    if (path.startsWith("/api/v1/admin/coupon")) return "coupon:review";
    if (path.startsWith("/api/v1/admin/post")) return "community:moderate";
    if (path.startsWith("/api/v1/admin/search")) return "search:manage";
    if (path.startsWith("/api/v1/admin/notification")) return "notification:manage";
    if (path.equals("/api/v1/admin/dashboard")) return "analytics:read";
    if (path.startsWith("/api/v1/admin/audit") || path.startsWith("/api/v1/admin/operation-audit"))
      return "audit:read";
    return null;
  }

  public static boolean allowed(String portal, String permission) {
    if (!java.util.Set.of("USER", "MERCHANT", "ADMIN", "SHARED").contains(portal)) return false;
    if (!"SHARED".equals(portal)
        && !CurrentActor.portal().name().equals(portal)
        && !CurrentActor.isBusinessAdministrator()) return false;
    if (permission == null) return true;
    try {
      CurrentActor.require(permission);
      return true;
    } catch (com.campus.perks.common.exception.ApplicationException denied) {
      return false;
    }
  }
}
