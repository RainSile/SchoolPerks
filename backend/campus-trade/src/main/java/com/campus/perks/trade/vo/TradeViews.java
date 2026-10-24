package com.campus.perks.trade.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public final class TradeViews {
  private TradeViews() {}

  public record Offer(
      String id,
      String merchantId,
      String title,
      String description,
      String kind,
      String price,
      int stockAvailable,
      int stockTotal,
      Instant startsAt,
      Instant endsAt,
      int groupSize,
      int groupDurationSeconds,
      String status,
      int version) {}

  public record Order(
      String id,
      String orderNo,
      String merchantId,
      String offerId,
      String groupId,
      String amount,
      String currency,
      JsonNode offerSnapshot,
      String status,
      Instant expiresAt,
      Instant paidAt,
      Instant createdAt) {}

  public record Group(
      String id,
      String offerId,
      int targetSize,
      int reservedCount,
      int paidCount,
      String status,
      Instant expiresAt) {}

  public record Payment(
      String id, String orderId, String status, String checkoutUrl, Instant expiresAt) {}

  public record Refund(
      String id,
      String orderId,
      String amount,
      String status,
      int attempts,
      String lastErrorCode) {}

  public record RedemptionCode(String orderId, String code, Instant expiresAt) {}

  public record Result<T>(boolean created, T value) {}
}
