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
@Tag(name = "领券与到店核销")
@SecurityRequirement(name = "bearerAuth")
public class CouponClaimController {
  private final CouponService service;

  public CouponClaimController(CouponService service) {
    this.service = service;
  }

  @PostMapping("/coupons/{id}/claims")
  @Operation(summary = "同步领取一张优惠券", description = "MySQL事务扣库存；每人每券一次；首次201，重复请求200并返回已有券，不重复扣库存。")
  public ResponseEntity<CouponClaim> claim(@PathVariable String id) {
    var result = service.claim(id);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .location(URI.create("/api/v1/coupon-claims/" + result.value().id()))
        .body(result.value());
  }

  @GetMapping("/users/me/coupon-claims")
  @Operation(summary = "分页查询本人优惠券")
  public PageResponse<CouponClaim> claims(@Valid @ParameterObject PageQuery q) {
    return service.ownClaims(q);
  }

  @GetMapping("/coupon-claims/{id}")
  @Operation(summary = "查看本人券状态", description = "不返回核销码或摘要；其他人404；过期状态按服务器时间派生。")
  public CouponClaim claimDetail(@PathVariable String id) {
    return service.ownClaim(id);
  }

  @PostMapping("/coupon-claims/{id}/redemption-tickets")
  @Operation(summary = "本人生成短时核销二维码", description = "轮换后旧码失效；摘要入库、明文仅本次响应、no-store；有生成频率限制。")
  public ResponseEntity<RedemptionTicket> ticket(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.ticket(id));
  }

  @PostMapping("/redemptions")
  @Operation(
      summary = "本店店主、经理或核销店员完成核销",
      description = "请求体merchantId/code；验证门店/摘要/期限并原子核销。首次201，持相同有效凭据重试200；审计和通知outbox同事务写入。")
  public ResponseEntity<RedemptionView> redeem(@Valid @RequestBody CreateRedemptionRequest r) {
    var result = service.redeem(r);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .location(URI.create("/api/v1/redemptions/" + result.value().id()))
        .body(result.value());
  }

  @GetMapping("/redemptions/{id}")
  @Operation(summary = "本店成员查询核销凭证")
  public RedemptionView redemption(@PathVariable String id) {
    return service.redemption(id);
  }

  @GetMapping("/merchants/{merchantId}/redemptions")
  @Operation(summary = "本店成员分页查询核销历史")
  public PageResponse<RedemptionView> list(
      @PathVariable String merchantId, @Valid @ParameterObject PageQuery q) {
    return service.redemptions(merchantId, q);
  }
}
