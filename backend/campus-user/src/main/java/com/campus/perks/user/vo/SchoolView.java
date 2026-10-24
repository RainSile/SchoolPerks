package com.campus.perks.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "学校选项")
public record SchoolView(String id, String name) {
  @Override
  public String toString() {
    return "SchoolView[REDACTED]";
  }
}
