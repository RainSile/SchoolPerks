package com.campus.perks.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "CreateCampusVerificationRequest")
public record CreateCampusVerificationRequest(
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,17}") @Schema(description = "学校 ID") String schoolId,
    @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "[^\\p{Cntrl}]+")
        @Schema(description = "真实姓名，仅加密存储")
        String realName,
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+") @Schema(description = "学号，仅加密存储")
        String studentNumber,
    @NotBlank @Size(max=64) @Schema(description="本人上传并确认的STUDENT_CARD学生证首页图片ID")
        String studentCardFileId) {
  public CreateCampusVerificationRequest(String schoolId,String realName,String studentNumber) {
    this(schoolId,realName,studentNumber,null);
  }
  @Override
  public String toString() {
    return "CreateCampusVerificationRequest[REDACTED]";
  }
}
