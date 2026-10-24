package com.campus.perks.trade.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.trade")
public record TradeProperties(
    int batchSize,
    int maxStock,
    int maxGroupSize,
    Duration maxGroupDuration,
    Duration maxOfferDuration,
    int refundMaxAttempts,
    Duration retryBase,
    Duration retryMax,
    Duration leaseTtl,
    Duration paymentCheckInterval,
    Duration redemptionTtl,
    Duration redemptionResendInterval) {
  public TradeProperties {
    if (batchSize < 1
        || batchSize > 100
        || maxStock < 1
        || maxGroupSize < 2
        || refundMaxAttempts < 1
        || maxGroupDuration == null
        || maxOfferDuration == null
        || retryBase == null
        || retryMax == null
        || leaseTtl == null
        || paymentCheckInterval == null
        || maxGroupDuration.isNegative()
        || maxGroupDuration.isZero()
        || maxOfferDuration.isNegative()
        || maxOfferDuration.isZero()
        || retryBase.isNegative()
        || retryBase.isZero()
        || retryMax.compareTo(retryBase) < 0
        || leaseTtl.isNegative()
        || leaseTtl.isZero()
        || paymentCheckInterval.isNegative()
        || paymentCheckInterval.isZero()
        || redemptionTtl == null
        || redemptionTtl.isNegative()
        || redemptionTtl.isZero()
        || redemptionResendInterval == null
        || redemptionResendInterval.isNegative())
      throw new IllegalArgumentException("Invalid trade configuration");
  }
}
