package com.campus.perks.assistant.vo;

import java.time.Instant;
import java.util.List;

public final class MarketingViews {
  private MarketingViews() {}

  public record Capabilities(
      boolean enabled,
      int maxBriefLength,
      int maxTitleLength,
      int maxBodyLength,
      int dailyChatLimit,
      int dailyChatUsed,
      List<String> tones) {}

  public record Source(String type, String id, String title) {}

  public record Draft(
      String id,
      String merchantId,
      String createdBy,
      String brief,
      String tone,
      String couponId,
      String status,
      String title,
      String body,
      int version,
      int attempt,
      String errorCode,
      boolean sourceCurrent,
      List<Source> sources,
      Instant retryAfter,
      Instant createdAt,
      Instant updatedAt) {}

  public record Result(Draft value, boolean created) {}
}
