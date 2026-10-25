package com.campus.perks.assistant.dto;

import jakarta.validation.constraints.*;

public record SendMessageRequest(
    @NotBlank @Size(max = 4000) String message,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "仅允许本条消息明确要求的业务写操作，默认只读",
            defaultValue = "false")
        boolean allowWrites) {
  public SendMessageRequest(String message) {
    this(message, false);
  }

  @Override
  public String toString() {
    return "SendMessageRequest[REDACTED]";
  }
}
