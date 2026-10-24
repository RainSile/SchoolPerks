package com.campus.perks.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordLoginRequest(
    @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") @Schema(description = "手机号") String phone,
    @NotBlank @Size(max = 72) @Schema(description = "密码", format = "password") String password) {
  @Override
  public String toString() {
    return "PasswordLoginRequest[REDACTED]";
  }
}
