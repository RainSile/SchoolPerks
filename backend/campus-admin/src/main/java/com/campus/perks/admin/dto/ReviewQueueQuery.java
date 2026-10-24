package com.campus.perks.admin.dto;

import com.campus.perks.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "按状态分页查询审核申请")
public class ReviewQueueQuery extends PageQuery {
  @NotBlank
  @Pattern(regexp = "PENDING|APPROVED|REJECTED")
  private String status = "PENDING";

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
