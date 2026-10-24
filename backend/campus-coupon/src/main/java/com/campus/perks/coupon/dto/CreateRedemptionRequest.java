package com.campus.perks.coupon.dto;

import jakarta.validation.constraints.*;

public record CreateRedemptionRequest(
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,17}") String merchantId,
    @NotBlank @Size(max = 80) String code) {
  @Override
  public String toString() {
    return "CreateRedemptionRequest[REDACTED]";
  }
}
