package com.campus.perks.coupon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateCouponRequest(
    @NotNull @Min(0) Integer version, @NotNull @Valid CouponInput content) {}
