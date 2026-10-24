package com.campus.perks.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "UpdateMerchantRequest")
public record UpdateMerchantRequest(
    @Min(0) @NotNull Integer version,
    @NotNull @Size(max = 1000) String description,
    @NotNull @Size(max = 255) String businessHours) {}
