package com.campus.perks.merchant.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.integration.port.*;
import com.campus.perks.merchant.config.MerchantProperties;
import com.campus.perks.merchant.dto.*;
import com.campus.perks.merchant.service.MerchantWorkflowService;
import com.campus.perks.merchant.vo.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "商家入驻与门店管理")
public class MerchantWorkflowController {
  private final MerchantWorkflowService service;
  private final MerchantProperties properties;

  public MerchantWorkflowController(
      MerchantWorkflowService service, MerchantProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  @GetMapping("/merchant-categories")
  @Operation(summary = "查询可选商家分类")
  public Set<String> categories() {
    return properties.categories();
  }

  @PostMapping("/merchant-applications")
  @Operation(summary = "提交本人商家入驻申请", description = "必须关联本人已确认的私有资质文件；审核前不创建公开门店。")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<MerchantReviewPort.Application> submit(
      @Valid @RequestBody CreateMerchantApplicationRequest r) {
    var a = service.submit(r);
    return ResponseEntity.created(URI.create("/api/v1/merchant-applications/" + a.id())).body(a);
  }

  @GetMapping("/users/me/merchant-applications")
  @Operation(summary = "分页查询本人入驻申请")
  @SecurityRequirement(name = "bearerAuth")
  public PageResponse<MerchantReviewPort.Application> applications(
      @Valid @ParameterObject PageQuery q) {
    return service.applications(q);
  }

  @GetMapping("/merchant-applications/{id}")
  @Operation(summary = "查询本人入驻申请详情")
  @SecurityRequirement(name = "bearerAuth")
  public MerchantReviewPort.Application application(@PathVariable String id) {
    return service.application(id);
  }

  @GetMapping("/users/me/merchants")
  @Operation(summary = "查询本人所属门店")
  @SecurityRequirement(name = "bearerAuth")
  public PageResponse<MerchantWorkspace> merchants(@Valid @ParameterObject PageQuery q) {
    return service.merchants(q);
  }

  @GetMapping("/merchants/{id}/workspace")
  @Operation(summary = "查询本店工作台资料", description = "必须是该门店成员；不是成员按 404 处理。")
  @SecurityRequirement(name = "bearerAuth")
  public MerchantWorkspace workspace(@PathVariable String id) {
    return service.workspace(id);
  }

  @PatchMapping("/merchants/{id}")
  @Operation(summary = "店主修改门店介绍与营业时间", description = "白名单字段和版本校验；不能直接改审核过的名称、地址、分类、学校或资质。")
  @SecurityRequirement(name = "bearerAuth")
  public MerchantWorkspace update(
      @PathVariable String id, @Valid @RequestBody UpdateMerchantRequest r) {
    return service.update(id, r);
  }

  @GetMapping("/merchants/{id}/members")
  @Operation(summary = "店主分页查询本店成员")
  @SecurityRequirement(name = "bearerAuth")
  public PageResponse<MemberView> members(
      @PathVariable String id, @Valid @ParameterObject PageQuery q) {
    return service.members(id, q);
  }

  @PostMapping("/merchants/{id}/members")
  @Operation(summary = "店主添加本店店员", description = "目标必须是已存在的活跃用户；只能授予 MANAGER/CASHIER，不授予平台角色。")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<MemberView> add(
      @PathVariable String id, @Valid @RequestBody CreateMemberRequest r) {
    var m = service.addMember(id, r);
    return ResponseEntity.created(URI.create("/api/v1/merchants/" + id + "/members/" + m.userId()))
        .body(m);
  }

  @GetMapping("/merchants/{id}/members/{userId}")
  @Operation(summary = "店主查询本店单个成员")
  @SecurityRequirement(name = "bearerAuth")
  public MemberView member(@PathVariable String id, @PathVariable String userId) {
    return service.member(id, userId);
  }

  @DeleteMapping("/merchants/{id}/members/{userId}")
  @Operation(summary = "店主移除店员", description = "不能移除店主；移除后下一次请求立即失去本店访问权。")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<Void> remove(@PathVariable String id, @PathVariable String userId) {
    service.removeMember(id, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/merchants/{id}/qualification-download-tickets")
  @Operation(summary = "店主创建本店私有资质下载凭据")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<FileAccessPort.Download> qualification(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.qualification(id));
  }
}
