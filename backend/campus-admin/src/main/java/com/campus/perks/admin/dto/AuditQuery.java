package com.campus.perks.admin.dto;

import com.campus.perks.common.api.PageQuery;
import jakarta.validation.constraints.*;

public class AuditQuery extends PageQuery {
  @Pattern(regexp = "[A-Z_]{1,64}")
  private String resourceType;

  @Size(max = 64)
  private String resourceId;

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String value) {
    resourceType = value;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String value) {
    resourceId = value;
  }
}
