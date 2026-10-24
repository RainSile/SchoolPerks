package com.campus.perks.admin.controller;

import com.campus.perks.admin.dto.OperationAuditQuery;
import com.campus.perks.admin.service.OperationAuditService;
import com.campus.perks.common.api.PageResponse;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.security.port.OperationAuditPort.Event;
import com.campus.perks.security.service.AuditedOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/operation-audit-events")
@Tag(name = "操作结果审计")
@SecurityRequirement(name = "bearerAuth")
public class OperationAuditController {
  private final ObjectProvider<OperationAuditService> services;

  public OperationAuditController(ObjectProvider<OperationAuditService> services) {
    this.services = services;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('audit:read')")
  @AuditedOperation(action = "OPERATION_AUDIT_QUERIED", resourceType = "OPERATION_AUDIT")
  @Operation(summary = "按时间、操作者、动作和结果分页查询操作审计，不含请求或响应正文")
  public ResponseEntity<PageResponse<Event>> query(@Valid @ParameterObject OperationAuditQuery q) {
    var service = services.getIfAvailable();
    if (service == null)
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "AUDIT_UNAVAILABLE", "操作审计服务尚未启用");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.query(q));
  }
}
