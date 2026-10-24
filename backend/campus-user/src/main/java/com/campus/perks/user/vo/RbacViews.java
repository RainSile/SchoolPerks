package com.campus.perks.user.vo;

import java.util.List;

public final class RbacViews {
  private RbacViews() {}

  public record Role(
      String id,
      String code,
      String name,
      String accountType,
      int version,
      List<String> permissions) {}

  public record Permission(String code, String name) {}

  public record Account(
      String id,
      String nickname,
      String accountType,
      String status,
      int version,
      List<String> roleIds) {}
}
