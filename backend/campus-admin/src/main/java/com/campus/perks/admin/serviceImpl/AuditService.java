package com.campus.perks.admin.serviceImpl;

import com.campus.perks.admin.mapper.AuditMapper;
import com.campus.perks.integration.port.AuditPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "campus.database.enabled", havingValue = "true")
public class AuditService implements AuditPort, com.campus.perks.admin.service.AuditQueryService {
  private final AuditMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public AuditService(AuditMapper mapper, ObjectMapper json, Clock clock) {
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public String record(
      String actor, String action, String type, String resource, Map<String, Object> details) {
    try {
      String id = UUID.randomUUID().toString();
      mapper.insert(
          id,
          Long.parseLong(actor),
          action,
          type,
          resource,
          json.writeValueAsString(details),
          clock.instant());
      return id;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Audit serialization failed");
    }
  }

  public com.campus.perks.common.api.PageResponse<com.campus.perks.admin.vo.AuditEventView> query(
      com.campus.perks.admin.dto.AuditQuery q) {
    com.campus.perks.security.service.CurrentActor.require("audit:read");
    return new com.campus.perks.common.api.PageResponse<>(
        mapper
            .list(
                q.getResourceType(),
                q.getResourceId(),
                q.getSize(),
                (long) (q.getPage() - 1) * q.getSize())
            .stream()
            .map(this::view)
            .toList(),
        mapper.count(q.getResourceType(), q.getResourceId()),
        q.getPage(),
        q.getSize());
  }

  public com.campus.perks.admin.vo.AuditEventView get(String id) {
    com.campus.perks.security.service.CurrentActor.require("audit:read");
    var row = mapper.find(id);
    if (row == null)
      throw new com.campus.perks.common.exception.ApplicationException(
          org.springframework.http.HttpStatus.NOT_FOUND, "AUDIT_NOT_FOUND", "审计记录不存在");
    return view(row);
  }

  private com.campus.perks.admin.vo.AuditEventView view(
      com.campus.perks.admin.entity.AuditEventRow row) {
    try {
      return new com.campus.perks.admin.vo.AuditEventView(
          row.id(),
          row.actorId(),
          row.action(),
          row.resourceType(),
          row.resourceId(),
          json.readTree(row.details()),
          row.createdAt());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Audit data unreadable");
    }
  }
}
