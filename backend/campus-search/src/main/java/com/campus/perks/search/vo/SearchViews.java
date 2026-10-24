package com.campus.perks.search.vo;

import java.time.Instant;
import java.util.List;

public final class SearchViews {
  private SearchViews() {}

  public record Results(
      String backend,
      List<SearchHit> items,
      long total,
      int page,
      int size,
      boolean windowLimited) {}

  public record Rebuild(
      String id,
      String status,
      long processed,
      String cursorType,
      String cursorId,
      String errorCode,
      Instant createdAt,
      Instant completedAt) {}
}
