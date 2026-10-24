package com.campus.perks.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RegistrationRequest(
    @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") @Schema(description = "手机号") String phone,
    @NotBlank @Size(max = 64) @Schema(description = "注册验证码挑战 ID") String challengeId,
    @NotBlank @Pattern(regexp = "[0-9]{4,8}") @Schema(description = "注册验证码") String code,
    @NotBlank @Size(max = 64) @Schema(description = "昵称") String nickname,
    @NotBlank @Size(max = 72) @Schema(description = "密码", format = "password") String password) {
  @Override
  public String toString() {
    return "RegistrationRequest[REDACTED]";
  }
}
