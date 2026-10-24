package com.campus.perks.admin.controller;

import com.campus.perks.admin.dto.*;
import com.campus.perks.common.api.PageResponse;
import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.CampusReviewPort;
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
@Tag(name = "校园认证审核")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('campus:review')")
public class CampusReviewController {
  private final CampusReviewPort reviews;

  public CampusReviewController(CampusReviewPort reviews) {
    this.reviews = reviews;
  }

  @GetMapping("/campus-verifications")
  @Operation(summary = "分页查询校园认证申请", description = "队列不包含真实姓名和学号。")
  public PageResponse<CampusReviewPort.Entry> list(@Valid @ParameterObject ReviewQueueQuery q) {
    return reviews.applications(q.getPage(), q.getSize(), q.getStatus());
  }

  @GetMapping("/campus-verifications/{id}/identity")
  @Operation(summary = "授权审核员读取加密身份资料", description = "单独校验 campus:review 并记录访问审计，响应禁止缓存。")
  public ResponseEntity<CampusReviewPort.Identity> identity(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.identity(id));
  }

  @PostMapping("/campus-verifications/{id}/reviews")
  @Operation(summary = "审核校园认证", description = "通过时同事务更新校园身份、审核历史及审计；禁止自审。")
  public ResponseEntity<ReviewRecord> decide(
      @PathVariable String id, @Valid @RequestBody ReviewDecisionRequest r) {
    var record = reviews.decide(id, r.decision(), r.reason());
    return ResponseEntity.created(
            URI.create("/api/v1/admin/campus-verification-reviews/" + record.id()))
        .body(record);
  }

  @GetMapping("/campus-verification-reviews/{id}")
  @Operation(summary = "查询校园认证审核历史记录")
  public ReviewRecord review(@PathVariable String id) {
    return reviews.review(id);
  }
}
