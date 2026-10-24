package com.campus.perks.coupon.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "campus.coupon")
public record CouponProperties(
    @Min(1) int maxStock,
    Duration maxValidity,
    Duration ticketTtl,
    Duration ticketResendInterval,
    Duration redemptionWindow,
    @Min(1) int redemptionWindowLimit) {
  public CouponProperties {
    for (Duration value :
        new Duration[] {maxValidity, ticketTtl, ticketResendInterval, redemptionWindow})
      if (value == null || value.toMillis() < 1)
        throw new IllegalArgumentException("Coupon durations must be positive");
  }
}
