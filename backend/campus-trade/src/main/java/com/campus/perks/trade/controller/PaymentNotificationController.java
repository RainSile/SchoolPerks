package com.campus.perks.trade.controller;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.SandboxPaymentPort;
import com.campus.perks.trade.serviceImpl.OrderServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "支付宝沙箱回调")
public class PaymentNotificationController {
  private final SandboxPaymentPort provider;
  private final ObjectProvider<OrderServiceImpl> service;

  public PaymentNotificationController(
      SandboxPaymentPort provider, ObjectProvider<OrderServiceImpl> service) {
    this.provider = provider;
    this.service = service;
  }

  @PostMapping(
      value = "/api/v1/payment-notifications/alipay-sandbox",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  @Operation(
      summary = "支付宝沙箱异步通知：RSA2验签、app/seller/金额匹配、回调幂等",
      description = "供应商协议入口使用form；只在事务提交成功后返回success。浏览器跳转不确认付款。")
  public String accept(@RequestParam MultiValueMap<String, String> input) {
    if (input.size() > 60
        || input.values().stream().anyMatch(v -> v.size() != 1 || v.get(0).length() > 8192))
      throw new ApplicationException(
          HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_NOTIFICATION", "支付通知格式无效");
    var workflow = service.getIfAvailable();
    if (workflow == null)
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "TRADE_UNAVAILABLE", "交易业务未启用");
    var params = new TreeMap<>(input.toSingleValueMap());
    var notice = provider.verify(params);
    // Hash an unambiguous length-prefixed representation, without storing raw provider/customer
    // data.
    var canonical = new StringBuilder();
    params.forEach(
        (k, v) ->
            canonical
                .append(k.length())
                .append(':')
                .append(k)
                .append(v.length())
                .append(':')
                .append(v));
    workflow.accept(notice, OrderServiceImpl.hash(canonical.toString()));
    return "success";
  }
}
