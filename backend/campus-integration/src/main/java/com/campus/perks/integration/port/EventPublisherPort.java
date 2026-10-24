package com.campus.perks.integration.port;

import java.time.Instant;
import java.util.Map;

/** 待实现 Outbox → RabbitMQ；eventId 是消费者幂等键，禁止成功后空实现丢弃消息。 */
public interface EventPublisherPort {
  void publish(Event event);

  record Event(
      String eventId,
      String type,
      String aggregateId,
      Instant occurredAt,
      Map<String, Object> payload) {}
}
