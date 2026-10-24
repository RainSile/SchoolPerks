package com.campus.perks.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "CreateChallengeRequest")
public record CreateChallengeRequest(
    @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") @Schema(description = "中国大陆手机号") String phone,
    @NotBlank
        @Pattern(regexp = "LOGIN|REGISTER|RESET_PASSWORD")
        @Schema(description = "LOGIN、REGISTER 或 RESET_PASSWORD")
        String purpose) {
  @Override
  public String toString() {
    return "CreateChallengeRequest[REDACTED]";
  }
}
