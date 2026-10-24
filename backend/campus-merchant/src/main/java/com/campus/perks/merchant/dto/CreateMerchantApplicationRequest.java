package com.campus.perks.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "CreateMerchantApplicationRequest")
public record CreateMerchantApplicationRequest(
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,17}") String schoolId,
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 64) String category,
    @NotBlank @Size(max = 255) String address,
    @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7)
        java.math.BigDecimal longitude,
    @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 7)
        java.math.BigDecimal latitude,
    @NotNull @Size(max = 1000) String description,
    @NotBlank @Size(max = 64) String qualificationFileId) {}
