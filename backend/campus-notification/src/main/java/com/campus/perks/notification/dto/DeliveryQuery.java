package com.campus.perks.notification.dto;

import com.campus.perks.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;

public class DeliveryQuery extends PageQuery {
  @Pattern(regexp = "PENDING|PROCESSING|PUBLISHED|FAILED")
  private String status = "FAILED";

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }
}
