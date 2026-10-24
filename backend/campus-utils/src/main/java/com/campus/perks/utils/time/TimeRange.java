package com.campus.perks.utils.time;

import java.time.Instant;
import java.util.Objects;

/** 统一使用左闭右开的时间范围；时区转换在系统边界完成。 */
public record TimeRange(Instant startInclusive, Instant endExclusive) {
  public TimeRange {
    Objects.requireNonNull(startInclusive);
    Objects.requireNonNull(endExclusive);
    if (!endExclusive.isAfter(startInclusive)) {
      throw new IllegalArgumentException("结束时间必须晚于开始时间");
    }
  }
}
