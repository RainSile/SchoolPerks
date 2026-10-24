package com.campus.perks.integration.port;

import java.time.Duration;

public interface RateLimitPort {
  boolean acquire(String key, int maximum, Duration window);
}
