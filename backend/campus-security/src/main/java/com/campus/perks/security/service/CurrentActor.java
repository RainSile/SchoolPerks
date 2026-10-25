package com.campus.perks.security.service;

import com.campus.perks.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentActor {
  private CurrentActor() {}

  public static String id() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !auth.getName().matches("[1-9][0-9]{0,17}"))
      throw new ApplicationException(HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY", "用户身份无效");
    return auth.getName();
  }

  public static void require(String permission) {
    id();
    if (SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .noneMatch(a -> a.getAuthority().equals(permission)))
      throw new ApplicationException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权执行此操作");
  }

  public static AccountPortal portal() {
    id();
    var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
    return java.util.Arrays.stream(AccountPortal.values())
        .filter(p -> authorities.stream().anyMatch(a -> a.getAuthority().equals(p.authority())))
        .findFirst()
        .orElseThrow(
            () -> new ApplicationException(HttpStatus.FORBIDDEN, "PORTAL_REQUIRED", "账号端身份不可用"));
  }

  public static void requirePortal(AccountPortal portal) {
    if (portal() != portal && !isBusinessAdministrator())
      throw new ApplicationException(HttpStatus.FORBIDDEN, "PORTAL_MISMATCH", "该账号不能访问其他端的功能");
  }

  public static boolean isBusinessAdministrator() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !authentication.getName().matches("[1-9][0-9]{0,17}")) return false;
    var permissions =
        authentication.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toSet());
    return permissions.contains(AccountPortal.ADMIN.authority())
        && permissions.contains("business:manage")
        && !permissions.contains(AccountPortal.USER.authority())
        && !permissions.contains(AccountPortal.MERCHANT.authority());
  }
}
