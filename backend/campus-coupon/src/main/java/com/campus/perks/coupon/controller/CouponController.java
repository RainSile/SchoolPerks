package com.campus.perks.coupon.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.coupon.dto.*;
import com.campus.perks.coupon.service.CouponService;
import com.campus.perks.coupon.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "优惠券浏览与商家管理")
public class CouponController {
  private final CouponService service;

  public CouponController(CouponService service) {
    this.service = service;
  }

  @GetMapping("/coupons")
  @Operation(summary = "分页浏览公开优惠", description = "仅已发布、未到期且门店审核通过的优惠；未来开始的活动可浏览但不可领取。")
  public PageResponse<CouponSummary> query(@Valid @ParameterObject CouponQuery q) {
    return service.query(q);
  }

  @GetMapping("/coupons/{id}")
  @Operation(summary = "查看公开优惠详情")
  public CouponSummary detail(@PathVariable String id) {
    return service.detail(id);
  }

  @PostMapping("/merchants/{merchantId}/coupons")
  @Operation(summary = "本店店主或经理创建优惠草稿")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<MerchantCoupon> create(
      @PathVariable String merchantId, @Valid @RequestBody CouponInput r) {
    var value = service.create(merchantId, r);
    return ResponseEntity.created(URI.create("/api/v1/coupons/" + value.id() + "/management"))
        .body(value);
  }

  @GetMapping("/merchants/{merchantId}/coupons")
  @Operation(summary = "本店成员分页查看优惠管理记录")
  @SecurityRequirement(name = "bearerAuth")
  public PageResponse<MerchantCoupon> list(
      @PathVariable String merchantId, @Valid @ParameterObject PageQuery q) {
    return service.merchantCoupons(merchantId, q);
  }

  @GetMapping("/coupons/{id}/management")
  @Operation(summary = "本店成员查看优惠草稿及管理信息")
  @SecurityRequirement(name = "bearerAuth")
  public MerchantCoupon managed(@PathVariable String id) {
    return service.managed(id);
  }

  @PatchMapping("/coupons/{id}")
  @Operation(summary = "店主或经理修改草稿", description = "version+content；送审中和发布后拒绝修改，修改会使旧审核许可失效。")
  @SecurityRequirement(name = "bearerAuth")
  public MerchantCoupon update(@PathVariable String id, @Valid @RequestBody UpdateCouponRequest r) {
    return service.update(id, r);
  }

  @PostMapping("/coupons/{id}/review-requests")
  @Operation(summary = "提交优惠内容审核", description = "保存不可变内容快照；待审核时禁止编辑；管理员通过后仍须商家显式发布。")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<CouponReviewRequest> submit(
      @PathVariable String id, @Valid @RequestBody VersionRequest r) {
    var value = service.submitReview(id, r.version());
    return ResponseEntity.created(URI.create("/api/v1/coupon-review-requests/" + value.id()))
        .body(value);
  }

  @GetMapping("/coupon-review-requests/{id}")
  @Operation(summary = "本店成员或优惠审核员查看送审快照")
  @SecurityRequirement(name = "bearerAuth")
  public CouponReviewRequest reviewRequest(@PathVariable String id) {
    return service.reviewRequest(id);
  }

  @PostMapping("/coupons/{id}/status-changes")
  @Operation(
      summary = "审核通过后发布或下架优惠",
      description = "PUBLISHED仅允许已获批草稿；OFFLINE停止新领券，已领券仍可在有效期内到店使用；下架后不能重新发布。")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<CouponStatusChange> change(
      @PathVariable String id, @Valid @RequestBody CouponStatusRequest r) {
    var value = service.changeStatus(id, r);
    return ResponseEntity.created(
            URI.create("/api/v1/coupons/" + id + "/status-changes/" + value.id()))
        .body(value);
  }

  @GetMapping("/coupons/{id}/status-changes/{changeId}")
  @Operation(summary = "本店成员查询优惠状态变更记录")
  @SecurityRequirement(name = "bearerAuth")
  public CouponStatusChange statusChange(@PathVariable String id, @PathVariable String changeId) {
    return service.statusChange(id, changeId);
  }
}
