package com.campus.perks.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "申请私有资质或社区图片上传，不接受客户端指定 owner、桶或对象 key")
public record CreateUploadRequest(
    @NotBlank @Size(max = 200) String fileName,
    @NotBlank @Size(max = 100) String contentType,
    @Min(1) long size,
    @NotBlank @Pattern(regexp = "MERCHANT_QUALIFICATION|COMMUNITY_IMAGE") String purpose) {
  @Override
  public String toString() {
    return "CreateUploadRequest[REDACTED]";
  }
}
