package com.campus.perks.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "选择默认头像或绑定本人上传并核验的头像，二选一")
public record AvatarRequest(@Size(max=64) String fileId, @Size(max=64) String defaultKey) {
  @JsonIgnore @AssertTrue(message = "fileId和defaultKey必须二选一且非空")
  public boolean isValidSelection() {
    return (fileId != null && !fileId.isBlank() && defaultKey == null)
        || (defaultKey != null && !defaultKey.isBlank() && fileId == null);
  }
}
