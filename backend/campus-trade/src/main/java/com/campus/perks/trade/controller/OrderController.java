package com.campus.perks.trade.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.trade.dto.TradeRequests.*;
import com.campus.perks.trade.service.OrderService;
import com.campus.perks.trade.vo.TradeViews.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "套餐、订单与拼团")
public class OrderController {
  private final OrderService service;

  public OrderController(OrderService service) {
    this.service = service;
  }

  private <T> ResponseEntity<T> result(Result<T> result, String location) {
    return ResponseEntity.status(result.created() ? 201 : 200)
        .location(URI.create(location))
        .header("Cache-Control", "no-store")
        .body(result.value());
  }

  @GetMapping("/trade-offers")
  @Operation(summary = "公开套餐、秒杀和拼团套餐列表")
  public PageResponse<Offer> catalog(@Valid @ParameterObject PageQuery q) {
    return service.catalog(q);
  }

  @GetMapping("/trade-offers/{id}")
  @Operation(summary = "公开套餐详情，价格与内容发布后不可修改")
  public Offer offer(@PathVariable String id) {
    return service.offer(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/merchants/{merchantId}/trade-offers")
  @Operation(summary = "本店套餐列表")
  public PageResponse<Offer> merchantOffers(
      @PathVariable String merchantId, @Valid @ParameterObject PageQuery q) {
    return service.merchantOffers(merchantId, q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/merchants/{merchantId}/trade-offers")
  @Operation(summary = "有发布资格的本店 OWNER/MANAGER 创建套餐草稿")
  public ResponseEntity<Offer> createOffer(
      @PathVariable String merchantId, @Valid @RequestBody OfferInput input) {
    var o = service.createOffer(merchantId, input);
    return ResponseEntity.created(
            URI.create("/api/v1/merchants/" + merchantId + "/trade-offers/" + o.id()))
        .body(o);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/merchants/{merchantId}/trade-offers/{offerId}")
  @Operation(summary = "本店套餐草稿或发布内容详情")
  public Offer managedOffer(@PathVariable String merchantId, @PathVariable String offerId) {
    return service.managedOffer(merchantId, offerId);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/merchants/{merchantId}/orders/{orderId}")
  @Operation(summary = "本店订单详情，不返回买家身份")
  public Order merchantOrder(@PathVariable String merchantId, @PathVariable String orderId) {
    return service.merchantOrder(merchantId, orderId);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PatchMapping("/trade-offers/{id}")
  @Operation(summary = "发布或下架套餐，必须携带当前 version")
  public Offer changeOffer(@PathVariable String id, @Valid @RequestBody OfferStatus input) {
    return service.changeOffer(id, input);
  }

  @GetMapping("/trade-offers/{id}/group-purchases")
  @Operation(summary = "可参与的拼团，不返回成员身份")
  public PageResponse<Group> groups(@PathVariable String id, @Valid @ParameterObject PageQuery q) {
    return service.groups(id, q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/group-purchases/{id}")
  @Operation(summary = "拼团进度，不返回成员身份")
  public Group group(@PathVariable String id) {
    return service.group(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/orders")
  @Operation(
      summary = "服务端定价下单，一单一份；Idempotency-Key 必填",
      description = "GROUP 套餐省略 groupId 表示开团，提供 groupId 表示参团；不接收用户 ID 或客户端金额。")
  public ResponseEntity<Order> create(
      @Valid @RequestBody OrderInput input, @RequestHeader("Idempotency-Key") String key) {
    var r = service.createOrder(input, key);
    return result(r, "/api/v1/orders/" + r.value().id());
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/orders")
  @Operation(summary = "本人订单分页")
  public PageResponse<Order> orders(@Valid @ParameterObject PageQuery q) {
    return service.orders(q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/orders/{id}")
  @Operation(summary = "本人订单详情和不可变金额快照")
  public Order order(@PathVariable String id) {
    return service.order(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/merchants/{merchantId}/orders")
  @Operation(summary = "本店成员查询本店订单，不返回买家身份")
  public PageResponse<Order> merchantOrders(
      @PathVariable String merchantId, @Valid @ParameterObject PageQuery q) {
    return service.merchantOrders(merchantId, q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/orders/{id}/cancellations")
  @Operation(summary = "取消本人待支付订单；重复取消200，新取消201")
  public ResponseEntity<Order> cancel(@PathVariable String id) {
    return result(service.cancel(id), "/api/v1/orders/" + id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/orders/{id}/payments")
  @Operation(summary = "创建或恢复同一支付宝沙箱支付尝试；未配置503")
  public ResponseEntity<Payment> payment(@PathVariable String id) {
    var r = service.payment(id);
    return result(r, "/api/v1/orders/" + id + "/payments/" + r.value().id());
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/orders/{id}/payments/{paymentId}")
  @Operation(summary = "本人支付尝试状态，不返回签名地址")
  public Payment payment(@PathVariable String id, @PathVariable String paymentId) {
    return service.payment(id, paymentId);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/orders/{id}/refunds")
  @Operation(summary = "申请本人未核销已付款订单的全额退款，PENDING不代表已退款")
  public ResponseEntity<Refund> refund(@PathVariable String id) {
    var r = service.refund(id);
    return result(r, "/api/v1/orders/" + id + "/refunds/" + r.value().id());
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/orders/{id}/refunds")
  @Operation(summary = "查询本人订单的退款资源（至多一条）")
  public java.util.List<Refund> refunds(@PathVariable String id) {
    return service.refunds(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/orders/{id}/refunds/{refundId}")
  @Operation(summary = "查询本人退款及重试状态")
  public Refund refund(@PathVariable String id, @PathVariable String refundId) {
    return service.refund(id, refundId);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/orders/{id}/refunds/{refundId}/retries")
  @Operation(summary = "本人重试已耗尽自动重试的退款；保留原供应商幂等号")
  public ResponseEntity<Refund> retry(@PathVariable String id, @PathVariable String refundId) {
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/orders/" + id + "/refunds/" + refundId))
        .body(service.retryRefund(id, refundId));
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/orders/{id}/redemption-codes")
  @Operation(summary = "生成本人已付款订单短期核销码；数据库只存摘要")
  public ResponseEntity<RedemptionCode> code(@PathVariable String id) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.code(id));
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/merchants/{merchantId}/order-redemptions")
  @Operation(summary = "本店 OWNER/MANAGER/CASHIER 核销订单；同码重试幂等")
  public ResponseEntity<Order> redeem(
      @PathVariable String merchantId, @Valid @RequestBody Redemption input) {
    var r = service.redeem(merchantId, input);
    return result(r, "/api/v1/merchants/" + merchantId + "/orders/" + r.value().id());
  }
}
