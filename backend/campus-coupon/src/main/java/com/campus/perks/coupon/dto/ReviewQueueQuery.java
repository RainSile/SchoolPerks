package com.campus.perks.coupon.dto;

import com.campus.perks.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;

public class ReviewQueueQuery extends PageQuery {
  @Pattern(regexp = "PENDING|APPROVED|REJECTED")
  private String status = "PENDING";

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }
}
