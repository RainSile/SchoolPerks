package com.campus.perks.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "RefreshSessionRequest")
public record RefreshSessionRequest(
    @NotBlank @Size(min = 43, max = 43) @Schema(description = "不透明刷新令牌") String refreshToken) {
  @Override
  public String toString() {
    return "RefreshSessionRequest[REDACTED]";
  }
}
