package com.campus.perks.integration.adapter;

import com.alipay.api.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.AlipaySandboxProperties;
import com.campus.perks.integration.port.SandboxPaymentPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AlipaySandboxProperties.class)
public class AlipaySandboxAdapter implements SandboxPaymentPort {
  private final AlipaySandboxProperties properties;
  private final ObjectMapper json;
  private final AlipayClient client;

  public AlipaySandboxAdapter(AlipaySandboxProperties properties, ObjectMapper json) {
    this.properties = properties;
    this.json = json;
    if (!properties.enabled()) {
      client = null;
      return;
    }
    URI gateway = URI.create(properties.gateway());
    if (!"https".equals(gateway.getScheme())
        || gateway.getUserInfo() != null
        || gateway.getPort() != -1
        || gateway.getQuery() != null
        || gateway.getFragment() != null
        || !Set.of("openapi.alipaydev.com", "openapi-sandbox.dl.alipaydev.com")
            .contains(gateway.getHost())
        || !"/gateway.do".equals(gateway.getPath()))
      throw new IllegalStateException("Only the official HTTPS Alipay sandbox gateway is allowed");
    for (String value :
        List.of(
            properties.appId(),
            properties.sellerId(),
            properties.privateKey(),
            properties.publicKey(),
            properties.notifyUrl()))
      if (value.isBlank())
        throw new IllegalStateException("Alipay sandbox configuration incomplete");
    URI notify = URI.create(properties.notifyUrl());
    if (!"https".equals(notify.getScheme())
        || notify.getHost() == null
        || notify.getUserInfo() != null
        || notify.getFragment() != null)
      throw new IllegalStateException("Alipay callback requires a public HTTPS URL");
    try {
      var config = new AlipayConfig();
      config.setServerUrl(properties.gateway());
      config.setAppId(properties.appId());
      config.setPrivateKey(properties.privateKey());
      config.setAlipayPublicKey(properties.publicKey());
      config.setCharset("UTF-8");
      config.setSignType("RSA2");
      config.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
      config.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
      if (config.getConnectTimeout() <= 0 || config.getReadTimeout() <= 0)
        throw new IllegalStateException("Alipay timeouts must be positive");
      client = new DefaultAlipayClient(config);
    } catch (AlipayApiException e) {
      throw new IllegalStateException("Invalid Alipay sandbox keys");
    }
  }

  public boolean available() {
    return client != null;
  }

  private void requireAvailable() {
    if (!available()) throw unavailable();
  }

  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "SANDBOX_PAYMENT_UNAVAILABLE", "支付宝沙箱尚未配置或暂不可用");
  }

  private ApplicationException invalid() {
    return new ApplicationException(
        HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_NOTIFICATION", "支付通知校验失败");
  }

  private String body(Map<String, ?> values) {
    try {
      return json.writeValueAsString(values);
    } catch (Exception e) {
      throw new IllegalStateException("Payment serialization failed");
    }
  }

  public String checkout(String orderNo, BigDecimal amount, String subject, Instant expiresAt) {
    requireAvailable();
    var request = new AlipayTradePagePayRequest();
    request.setNotifyUrl(properties.notifyUrl());
    request.setBizContent(
        body(
            Map.of(
                "out_trade_no",
                orderNo,
                "total_amount",
                amount.toPlainString(),
                "subject",
                subject,
                "product_code",
                "FAST_INSTANT_TRADE_PAY",
                "time_expire",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Shanghai"))
                    .format(expiresAt))));
    try {
      return client.pageExecute(request, "GET").getBody();
    } catch (AlipayApiException e) {
      throw unavailable();
    }
  }

  public VerifiedPayment verify(Map<String, String> parameters) {
    requireAvailable();
    var p = new TreeMap<>(parameters);
    try {
      if (!"RSA2".equals(p.get("sign_type"))
          || !"utf-8".equalsIgnoreCase(p.get("charset"))
          || !AlipaySignature.rsaCheckV1(p, properties.publicKey(), "UTF-8", "RSA2")
          || !properties.appId().equals(p.get("app_id"))
          || !properties.sellerId().equals(p.get("seller_id"))) throw invalid();
      String notice = p.getOrDefault("notify_id", ""),
          order = p.getOrDefault("out_trade_no", ""),
          trade = p.getOrDefault("trade_no", ""),
          status = p.getOrDefault("trade_status", "");
      if (!notice.matches("[A-Za-z0-9_-]{1,128}")
          || !order.matches("[A-Za-z0-9]{1,64}")
          || !trade.matches("[A-Za-z0-9]{1,64}")
          || !Set.of("TRADE_SUCCESS", "TRADE_FINISHED", "TRADE_CLOSED", "WAIT_BUYER_PAY")
              .contains(status)) throw invalid();
      return new VerifiedPayment(
          notice, order, trade, new BigDecimal(p.get("total_amount")), status);
    } catch (AlipayApiException | IllegalArgumentException | NullPointerException e) {
      throw invalid();
    }
  }

  public PaymentState query(String orderNo) {
    requireAvailable();
    var request = new AlipayTradeQueryRequest();
    request.setBizContent(body(Map.of("out_trade_no", orderNo)));
    try {
      var response = client.execute(request);
      if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode()))
        return new PaymentState(orderNo, null, null, "NOT_FOUND");
      if (!response.isSuccess() || !orderNo.equals(response.getOutTradeNo())) throw unavailable();
      return new PaymentState(
          orderNo,
          response.getTradeNo(),
          new BigDecimal(response.getTotalAmount()),
          response.getTradeStatus());
    } catch (AlipayApiException | NumberFormatException e) {
      throw unavailable();
    }
  }

  public void close(String orderNo) {
    requireAvailable();
    var request = new AlipayTradeCloseRequest();
    request.setBizContent(body(Map.of("out_trade_no", orderNo)));
    try {
      var response = client.execute(request);
      if (!response.isSuccess()
          && !Set.of("ACQ.TRADE_NOT_EXIST", "ACQ.TRADE_STATUS_ERROR")
              .contains(Objects.toString(response.getSubCode(), ""))) throw unavailable();
    } catch (AlipayApiException e) {
      throw unavailable();
    }
  }

  public void refund(String orderNo, String refundId, BigDecimal amount) {
    requireAvailable();
    var request = new AlipayTradeRefundRequest();
    request.setBizContent(
        body(
            Map.of(
                "out_trade_no",
                orderNo,
                "out_request_no",
                refundId,
                "refund_amount",
                amount.toPlainString())));
    try {
      var response = client.execute(request);
      if (!response.isSuccess()
          || !orderNo.equals(response.getOutTradeNo())
          || new BigDecimal(response.getRefundFee()).compareTo(amount) != 0) throw unavailable();
    } catch (AlipayApiException | NumberFormatException e) {
      throw unavailable();
    }
  }
}
