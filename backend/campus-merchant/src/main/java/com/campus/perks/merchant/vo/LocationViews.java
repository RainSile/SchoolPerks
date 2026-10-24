package com.campus.perks.merchant.vo;

import java.math.BigDecimal;
import java.time.Instant;

public final class LocationViews {
  private LocationViews() {}

  public record Location(
      String merchantId,
      BigDecimal longitude,
      BigDecimal latitude,
      String coordinateSystem,
      int version,
      Instant updatedAt) {}

  public record Nearby(
      String id,
      String name,
      String category,
      String address,
      BigDecimal longitude,
      BigDecimal latitude,
      String coordinateSystem,
      long distanceMeters) {}
}
