package com.campus.perks.community.dto;

import com.campus.perks.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;

public class ModerationQuery extends PageQuery {
  @Pattern(regexp = "PENDING|APPROVED|REJECTED|HIDDEN|OPEN|RESOLVED|DISMISSED")
  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }
}
