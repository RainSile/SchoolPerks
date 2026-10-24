package com.campus.perks.trade.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class TradeRequests {
  private TradeRequests() {}

  public record OfferInput(
      @NotBlank @Size(max = 120) String title,
      @NotNull @Size(max = 2000) String description,
      @NotNull Kind kind,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
      @Min(1) int stock,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @Min(1) int groupSize,
      @Min(0) int groupDurationSeconds) {}

  public enum Kind {
    REGULAR,
    SECKILL,
    GROUP
  }

  public record OfferStatus(@NotNull Status status, @Min(0) int version) {}

  public enum Status {
    PUBLISHED,
    OFFLINE
  }

  public record OrderInput(
      @NotBlank @Pattern(regexp = "[1-9][0-9]{0,17}") String offerId,
      @Pattern(regexp = "[1-9][0-9]{0,17}") String groupId) {}

  public record Redemption(
      @NotBlank @Pattern(regexp = "[1-9][0-9]{0,17}") String orderId,
      @NotBlank @Size(max = 64) String code) {}
}
