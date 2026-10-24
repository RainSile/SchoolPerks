package com.campus.perks.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;

@Schema(description = "优惠草稿内容；发布后规则与数量不可修改")
public record CouponInput(
    @NotBlank @Size(max = 160) String title,
    @NotBlank @Pattern(regexp = "AMOUNT_OFF|DISCOUNT") String couponType,
    @NotNull @Valid CouponRules rules,
    @Min(1) int totalStock,
    @NotNull Instant validFrom,
    @NotNull Instant validUntil,
    boolean campusOnly,
    @NotNull @Size(max = 1000) String description) {}
