package com.campus.perks.coupon.dto;

import jakarta.validation.constraints.*;

public record CouponStatusRequest(
    @NotNull @Min(0) Integer version,
    @NotBlank @Pattern(regexp = "PUBLISHED|OFFLINE") String status,
    @NotBlank @Size(max = 255) String reason) {}
