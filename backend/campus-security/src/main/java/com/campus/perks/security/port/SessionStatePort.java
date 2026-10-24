package com.campus.perks.security.port;

import java.util.List;

/** Persisted session/user state is authoritative; do not trust a stale JWT permission snapshot. */
public interface SessionStatePort {
  State load(String userId, String sessionId);

  record State(boolean active, List<String> permissions, String accountType) {
    public State(boolean active, List<String> permissions) {
      this(active, permissions, "USER");
    }
  }
}
