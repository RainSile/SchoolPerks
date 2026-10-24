package com.campus.perks.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "UpdateProfileRequest")
public record UpdateProfileRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "[^\\p{Cntrl}]+") @Schema(description = "昵称")
        String nickname) {
  @Override
  public String toString() {
    return "UpdateProfileRequest[REDACTED]";
  }
}
