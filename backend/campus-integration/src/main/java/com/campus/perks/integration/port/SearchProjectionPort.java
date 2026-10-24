package com.campus.perks.integration.port;

/** Explicit outbox routing for public search projection. Unknown event types must fail. */
public interface SearchProjectionPort {
  void handle(EventPublisherPort.Event event);
}
