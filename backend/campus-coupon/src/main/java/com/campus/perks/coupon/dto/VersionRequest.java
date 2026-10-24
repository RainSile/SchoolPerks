package com.campus.perks.coupon.dto;

import jakarta.validation.constraints.*;

public record VersionRequest(@NotNull @Min(0) Integer version) {}
