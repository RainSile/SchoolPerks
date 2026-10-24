package com.campus.perks.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record UpdateMarketingDraftRequest(
    @Min(0) @Schema(description = "读取时的版本") int version,
    @NotBlank @Size(max = 160) @Schema(description = "人工确认的文案标题") String title,
    @NotBlank @Size(max = 10000) @Schema(description = "人工确认的文案正文，纯文本") String body) {
  @Override
  public String toString() {
    return "UpdateMarketingDraftRequest[REDACTED]";
  }
}
