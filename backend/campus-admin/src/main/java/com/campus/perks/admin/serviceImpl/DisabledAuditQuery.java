package com.campus.perks.admin.serviceImpl;

import com.campus.perks.admin.dto.AuditQuery;
import com.campus.perks.admin.service.AuditQueryService;
import com.campus.perks.admin.vo.AuditEventView;
import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "campus.database.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class DisabledAuditQuery implements AuditQueryService {
  public PageResponse<AuditEventView> query(AuditQuery q) {
    throw new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "数据库尚未启用");
  }

  public AuditEventView get(String id) {
    throw new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "数据库尚未启用");
  }
}
