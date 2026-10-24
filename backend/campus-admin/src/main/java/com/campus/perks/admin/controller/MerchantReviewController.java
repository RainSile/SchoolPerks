package com.campus.perks.admin.controller;

import com.campus.perks.admin.dto.*;
import com.campus.perks.common.api.PageResponse;
import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "商家入驻审核")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('merchant:review')")
public class MerchantReviewController {
  private final MerchantReviewPort reviews;

  public MerchantReviewController(MerchantReviewPort reviews) {
    this.reviews = reviews;
  }

  @GetMapping("/merchant-applications")
  @Operation(summary = "按状态分页查询入驻申请")
  public PageResponse<MerchantReviewPort.Application> list(
      @Valid @ParameterObject ReviewQueueQuery q) {
    return reviews.applications(q.getPage(), q.getSize(), q.getStatus());
  }

  @GetMapping("/merchant-applications/{id}")
  @Operation(summary = "查询待审或已审入驻快照")
  public MerchantReviewPort.Application get(@PathVariable String id) {
    return reviews.inspect(id);
  }

  @PostMapping("/merchant-applications/{id}/reviews")
  @Operation(summary = "审核入驻并创建不可覆写的记录", description = "通过时同事务创建门店、店主关系和审计；并发审核只允许一次成功；禁止自审。")
  public ResponseEntity<ReviewRecord> decide(
      @PathVariable String id, @Valid @RequestBody ReviewDecisionRequest r) {
    var record = reviews.decide(id, r.decision(), r.reason());
    return ResponseEntity.created(URI.create("/api/v1/admin/merchant-reviews/" + record.id()))
        .body(record);
  }

  @PostMapping("/merchant-applications/{id}/qualification-download-tickets")
  @Operation(summary = "审核员创建申请所附资质的短时凭据", description = "仅通过申请关联访问资质，不能读取任意用户的未关联文件；记录审计。")
  public ResponseEntity<FileAccessPort.Download> qualification(@PathVariable String id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.reviewQualification(id));
  }

  @GetMapping("/merchants/{id}")
  @Operation(summary = "审核员查询门店当前状态和版本")
  public MerchantReviewPort.MerchantState merchant(@PathVariable String id) {
    return reviews.merchant(id);
  }

  @PostMapping("/merchants/{id}/status-changes")
  @Operation(summary = "暂停或恢复门店", description = "必须提供当前版本与原因；暂停后公开列表隐藏，店主只可查看而不可修改/发布。")
  public ResponseEntity<MerchantReviewPort.StatusChange> status(
      @PathVariable String id, @Valid @RequestBody StatusChangeRequest r) {
    var result = reviews.changeStatus(id, r.status(), r.version(), r.reason());
    return ResponseEntity.created(
            URI.create("/api/v1/admin/merchants/" + id + "/status-changes/" + result.id()))
        .body(result);
  }

  @GetMapping("/merchants/{id}/status-changes/{changeId}")
  @Operation(summary = "查询门店状态变更记录")
  public MerchantReviewPort.StatusChange statusChange(
      @PathVariable String id, @PathVariable String changeId) {
    return reviews.statusChange(id, changeId);
  }
}
