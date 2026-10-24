package com.campus.perks.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CreateMarketingDraftRequest(
    @NotBlank @Size(max = 2000) @Schema(description = "营销目标与受众，不得放入顾客隐私或凭据") String brief,
    @NotBlank
        @Pattern(regexp = "FRIENDLY|CONCISE|LIVELY")
        @Schema(description = "语气：FRIENDLY、CONCISE、LIVELY")
        String tone,
    @Pattern(regexp = "[1-9][0-9]{0,17}") @Schema(description = "可选：本店已公开且未过期的优惠ID")
        String couponId) {
  @Override
  public String toString() {
    return "CreateMarketingDraftRequest[REDACTED]";
  }
}
