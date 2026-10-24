package com.campus.perks.user.entity;

public record RateEntity(String rateKey, int requestCount, java.time.Instant windowStart) {
  @Override
  public String toString() {
    return "RateEntity[REDACTED]";
  }
}
