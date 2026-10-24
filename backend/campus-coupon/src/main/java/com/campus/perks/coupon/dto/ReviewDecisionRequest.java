package com.campus.perks.coupon.dto;

import jakarta.validation.constraints.*;

public record ReviewDecisionRequest(
    @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
    @NotBlank @Size(max = 255) String reason) {}
