package com.campus.perks.coupon.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.coupon.dto.*;
import com.campus.perks.coupon.service.CouponService;
import com.campus.perks.coupon.vo.*;
import com.campus.perks.integration.contract.ReviewRecord;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "优惠内容审核")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('coupon:review')")
public class CouponReviewController {
  private final CouponService service;

  public CouponReviewController(CouponService service) {
    this.service = service;
  }

  @GetMapping("/coupon-review-requests")
  @Operation(summary = "按状态分页查询优惠送审请求")
  public PageResponse<CouponReviewRequest> queue(@Valid @ParameterObject ReviewQueueQuery q) {
    return service.reviewQueue(q);
  }

  @PostMapping("/coupon-review-requests/{id}/reviews")
  @Operation(summary = "创建优惠审核决定", description = "需coupon:review；禁止审核本人或所属门店内容；重复审核409。")
  public ResponseEntity<ReviewRecord> decide(
      @PathVariable String id, @Valid @RequestBody ReviewDecisionRequest r) {
    var record = service.decide(id, r);
    return ResponseEntity.created(URI.create("/api/v1/admin/coupon-reviews/" + record.id()))
        .body(record);
  }

  @GetMapping("/coupon-reviews/{id}")
  @Operation(summary = "读取不可覆写的优惠审核记录")
  public ReviewRecord review(@PathVariable String id) {
    return service.review(id);
  }
}
