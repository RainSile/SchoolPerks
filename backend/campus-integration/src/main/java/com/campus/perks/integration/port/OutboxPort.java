package com.campus.perks.integration.port;

/**
 * Must participate in the business transaction. Payloads contain IDs/status only, never redemption
 * codes.
 */
public interface OutboxPort {
  void append(EventPublisherPort.Event event);
}
