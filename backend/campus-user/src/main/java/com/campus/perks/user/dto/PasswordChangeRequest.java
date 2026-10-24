package com.campus.perks.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordChangeRequest(
    @NotBlank @Size(max = 72) @Schema(description = "当前密码", format = "password")
        String currentPassword,
    @NotBlank @Size(max = 72) @Schema(description = "新密码", format = "password") String password) {
  @Override
  public String toString() {
    return "PasswordChangeRequest[REDACTED]";
  }
}
