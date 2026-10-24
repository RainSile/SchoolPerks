package com.campus.perks.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "不可覆写的审核决定")
public record ReviewDecisionRequest(
    @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
    @NotBlank @Size(max = 255) String reason) {}
