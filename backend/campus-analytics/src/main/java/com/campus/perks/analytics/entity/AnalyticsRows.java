package com.campus.perks.analytics.entity;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class AnalyticsRows {
  private AnalyticsRows() {}

  public record Day(LocalDate date, Instant start, Instant end) {}

  public record Window(
      LocalDate from, LocalDate to, String zone, Instant start, Instant end, List<Day> days) {}

  public record Event(LocalDate date, String kind, long count, BigDecimal amount) {}
}
