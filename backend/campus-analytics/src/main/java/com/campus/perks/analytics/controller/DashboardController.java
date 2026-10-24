package com.campus.perks.analytics.controller;

import com.campus.perks.analytics.dto.DashboardQuery;
import com.campus.perks.analytics.service.DashboardService;
import com.campus.perks.analytics.vo.DashboardSummary;
import com.campus.perks.security.service.AuditedOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "经营统计")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  private ResponseEntity<DashboardSummary> response(DashboardSummary summary) {
    return ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(summary);
  }

  @GetMapping("/api/v1/merchants/{merchantId}/dashboard")
  @Operation(summary = "查询所属门店经营统计", description = "仅本店店主或店长；日期包含首尾；短期缓存每次仍检查当前权限。")
  @PreAuthorize("hasAuthority('merchant:analytics:read')")
  @AuditedOperation(
      action = "MERCHANT_DASHBOARD_QUERIED",
      resourceType = "MERCHANT",
      resourceArgument = 0)
  public ResponseEntity<DashboardSummary> merchant(
      @PathVariable String merchantId, @Valid @ParameterObject DashboardQuery query) {
    return response(service.merchant(merchantId, query));
  }

  @GetMapping("/api/v1/admin/dashboard")
  @Operation(summary = "查询平台经营统计", description = "仅具有平台统计权限的管理员；支付金额为支付宝沙箱 CNY。")
  @PreAuthorize("hasAuthority('analytics:read')")
  @AuditedOperation(action = "PLATFORM_DASHBOARD_QUERIED", resourceType = "PLATFORM")
  public ResponseEntity<DashboardSummary> platform(@Valid @ParameterObject DashboardQuery query) {
    return response(service.platform(query));
  }

  @GetMapping("/api/v1/merchant/dashboard")
  @Operation(
      summary = "查询门店经营统计（兼容入口）",
      description = "必须传 merchantId，推荐使用 /merchants/{merchantId}/dashboard。")
  @PreAuthorize("hasAuthority('merchant:analytics:read')")
  @AuditedOperation(action = "MERCHANT_DASHBOARD_QUERIED", resourceType = "MERCHANT")
  public ResponseEntity<DashboardSummary> query(@Valid @ParameterObject DashboardQuery query) {
    return response(service.query(query));
  }
}
