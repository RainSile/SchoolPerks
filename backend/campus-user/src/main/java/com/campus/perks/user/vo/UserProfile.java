package com.campus.perks.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "本人资料，手机号已脱敏，权限来自服务端")
public record UserProfile(
    String id,
    String nickname,
    String maskedPhone,
    String schoolId,
    String schoolName,
    String campusVerificationStatus,
    java.util.List<String> permissions,
    String accountType,
    String homePath) {
  @Override
  public String toString() {
    return "UserProfile[REDACTED]";
  }
}
