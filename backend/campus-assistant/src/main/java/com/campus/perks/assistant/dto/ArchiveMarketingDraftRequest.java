package com.campus.perks.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record ArchiveMarketingDraftRequest(
    @Min(0) @Schema(description = "读取时的版本") int version,
    @NotBlank @Pattern(regexp = "ARCHIVED") @Schema(description = "仅支持归档，不存在发布状态") String status) {}
