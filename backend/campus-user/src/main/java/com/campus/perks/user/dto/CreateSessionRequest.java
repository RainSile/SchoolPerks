package com.campus.perks.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "CreateSessionRequest")
public record CreateSessionRequest(
    @NotBlank @Size(max = 64) @Schema(description = "验证码挑战 ID") String challengeId,
    @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") @Schema(description = "手机号") String phone,
    @NotBlank @Pattern(regexp = "[0-9]{4,8}") @Schema(description = "一次性验证码") String code) {
  @Override
  public String toString() {
    return "CreateSessionRequest[REDACTED]";
  }
}
