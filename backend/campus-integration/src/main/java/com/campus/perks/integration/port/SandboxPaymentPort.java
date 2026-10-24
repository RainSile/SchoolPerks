package com.campus.perks.integration.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Only Alipay sandbox. No production gateway or test-success switch. */
public interface SandboxPaymentPort {
  boolean available();

  String checkout(String orderNo, BigDecimal amount, String subject, Instant expiresAt);

  VerifiedPayment verify(Map<String, String> parameters);

  PaymentState query(String orderNo);

  void close(String orderNo);

  void refund(String orderNo, String refundId, BigDecimal amount);

  record VerifiedPayment(
      String notificationId, String orderNo, String tradeNo, BigDecimal amount, String status) {}

  record PaymentState(String orderNo, String tradeNo, BigDecimal amount, String status) {}
}
