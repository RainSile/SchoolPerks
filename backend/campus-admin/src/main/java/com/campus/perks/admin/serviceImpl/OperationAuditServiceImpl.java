package com.campus.perks.admin.serviceImpl;

import com.campus.perks.admin.dto.OperationAuditQuery;
import com.campus.perks.admin.mapper.OperationAuditMapper;
import com.campus.perks.admin.service.OperationAuditService;
import com.campus.perks.common.api.PageResponse;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.security.port.OperationAuditPort;
import com.campus.perks.security.service.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class OperationAuditServiceImpl implements OperationAuditPort, OperationAuditService {
  private final OperationAuditMapper db;
  private final Clock clock;
  private final int defaultDays, maxDays;

  public OperationAuditServiceImpl(
      OperationAuditMapper db,
      Clock clock,
      @Value("${campus.audit.default-days}") int defaultDays,
      @Value("${campus.audit.max-days}") int maxDays) {
    this.db = db;
    this.clock = clock;
    this.defaultDays = defaultDays;
    this.maxDays = maxDays;
    if (defaultDays < 1 || maxDays < defaultDays || maxDays > 366)
      throw new IllegalArgumentException("Invalid operation audit window");
  }

  // Controller interception runs after its business transaction; test outer transactions join here.
  @Transactional
  public void record(Event event) {
    db.insert(event);
  }

  @Transactional(readOnly = true)
  public PageResponse<Event> query(OperationAuditQuery q) {
    CurrentActor.requirePortal(AccountPortal.ADMIN);
    CurrentActor.require("audit:read");
    var from = q.getFrom();
    var to = q.getTo();
    if ((from == null) != (to == null))
      throw new ApplicationException(
          HttpStatus.BAD_REQUEST, "INVALID_AUDIT_RANGE", "审计开始和结束时间需同时提供");
    if (from == null) {
      to = clock.instant().plusMillis(1);
      from = to.minus(Duration.ofDays(defaultDays));
    }
    if (!to.isAfter(from)
        || Duration.between(from, to).compareTo(Duration.ofDays(maxDays)) > 0
        || (long) q.getPage() * q.getSize() > 10000)
      throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_RANGE", "审计范围或分页过大");
    return new PageResponse<>(
        db.list(q, from, to), db.count(q, from, to), q.getPage(), q.getSize());
  }
}
