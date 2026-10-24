package com.campus.perks.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "CreateMemberRequest")
public record CreateMemberRequest(
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,17}") String userId,
    @NotBlank @Pattern(regexp = "MANAGER|CASHIER") String role) {}
