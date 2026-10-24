package com.campus.perks.admin.dto;

import com.campus.perks.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

public class OperationAuditQuery extends PageQuery {
  @Pattern(regexp = "[A-Z0-9_]{1,100}")
  private String action;

  @Pattern(regexp = "SUCCESS|DENIED|FAILURE")
  private String outcome;

  @Pattern(regexp = "[1-9][0-9]{0,17}")
  private String actorId;

  @Schema(description = "UTC区间开始，与to同时提供")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant from;

  @Schema(description = "UTC区间结束，不含该时刻")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant to;

  public String getAction() {
    return action;
  }

  public void setAction(String v) {
    action = v;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String v) {
    outcome = v;
  }

  public String getActorId() {
    return actorId;
  }

  public void setActorId(String v) {
    actorId = v;
  }

  public Instant getFrom() {
    return from;
  }

  public void setFrom(Instant v) {
    from = v;
  }

  public Instant getTo() {
    return to;
  }

  public void setTo(Instant v) {
    to = v;
  }
}
