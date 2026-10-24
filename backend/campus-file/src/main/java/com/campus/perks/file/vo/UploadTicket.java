package com.campus.perks.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;

@Schema(description = "短时直传凭据，必须在上传后完成确认")
public record UploadTicket(String id, URI uploadUrl, String contentType, Instant expiresAt) {
  @Override
  public String toString() {
    return "UploadTicket[REDACTED]";
  }
}
