package com.campus.perks.analytics.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Schema(description = "统计结果；日期含首尾，实际查询为所选时区半开UTC区间；交易金额为沙箱CNY")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DashboardSummary(
    String scope,
    String merchantId,
    String merchantName,
    LocalDate from,
    LocalDate to,
    String zone,
    Instant startsAt,
    Instant endsAt,
    Instant calculatedAt,
    Instant cacheExpiresAt,
    String paymentEnvironment,
    String currency,
    Metrics metrics,
    List<Daily> daily,
    List<CouponRank> topCoupons,
    List<OrderStatus> orderStatuses,
    Long newAccounts,
    Long newMerchants,
    boolean pageViewUvAvailable,
    Long pageViewUv,
    List<String> allowedZones,
    int maxDays,
    List<String> definitions) {
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Metrics(
      long claimCount,
      long couponRedemptionCount,
      long cohortRedemptionCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cohortRedemptionRate,
      long createdOrderCount,
      long paidOrderCount,
      long orderRedemptionCount,
      long refundCount,
      long participatingUserCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal refundAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal netCashFlow) {}

  public record Daily(
      LocalDate date,
      long claimCount,
      long couponRedemptionCount,
      long createdOrderCount,
      long paidOrderCount,
      long orderRedemptionCount,
      long refundCount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal refundAmount) {}

  public record CouponRank(
      String couponId, String title, long claimCount, long cohortRedemptionCount) {}

  public record OrderStatus(String status, long count) {}
}
