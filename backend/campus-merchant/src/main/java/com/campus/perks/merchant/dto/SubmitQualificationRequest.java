package com.campus.perks.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "店主为既有门店补交本人已封存资质，仍须管理员审核")
public record SubmitQualificationRequest(
    @NotNull @Min(0) Integer version,
    @NotBlank @Size(max = 64) String qualificationFileId) {}
