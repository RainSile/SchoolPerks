package com.campus.perks.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

public record RetryMarketingDraftRequest(@Min(0) @Schema(description = "草稿当前版本") int version) {}
