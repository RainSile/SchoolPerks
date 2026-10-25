package com.campus.perks.assistant.serviceImpl;

import java.util.concurrent.atomic.AtomicInteger;

/** Request consent and durable turn identity are supplied by the application, never the model. */
public final class AssistantActionScope implements AutoCloseable {
  public record State(
      String turnId, String leaseToken, boolean allowWrites, AtomicInteger mutations) {}

  private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
  private final State previous;

  public AssistantActionScope(String turnId, String leaseToken, boolean allowWrites) {
    previous = CURRENT.get();
    CURRENT.set(new State(turnId, leaseToken, allowWrites, new AtomicInteger()));
  }

  public static State current() {
    return CURRENT.get();
  }

  public void close() {
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
  }
}
