package com.campus.perks.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "本人校园认证申请，不返回身份原文")
public record CampusVerificationView(
    String id,
    String schoolId,
    String schoolName,
    String status,
    java.time.Instant submittedAt,
    java.time.Instant reviewedAt,
    String reviewReason) {
  @Override
  public String toString() {
    return "CampusVerificationView[REDACTED]";
  }
}
