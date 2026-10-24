package com.campus.perks.admin.controller;

import com.campus.perks.admin.dto.AuditQuery;
import com.campus.perks.admin.service.AuditQueryService;
import com.campus.perks.admin.vo.AuditEventView;
import com.campus.perks.common.api.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@Tag(name = "操作审计")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('audit:read')")
public class AuditController {
  private final AuditQueryService service;

  public AuditController(AuditQueryService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "分页查询关键业务审计事件")
  public PageResponse<AuditEventView> list(@Valid @ParameterObject AuditQuery q) {
    return service.query(q);
  }

  @GetMapping("/{id}")
  @Operation(summary = "查询单条审计事件")
  public AuditEventView get(@PathVariable String id) {
    return service.get(id);
  }
}
