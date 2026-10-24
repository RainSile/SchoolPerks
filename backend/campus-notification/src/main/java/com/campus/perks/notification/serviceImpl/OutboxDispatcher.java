package com.campus.perks.notification.serviceImpl;

import com.campus.perks.integration.port.EventPublisherPort;
import com.campus.perks.notification.config.NotificationProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class OutboxDispatcher {
  private final OutboxQueue queue;
  private final EventPublisherPort publisher;
  private final ObjectMapper json;
  private final NotificationProperties properties;
  private final Environment environment;

  public OutboxDispatcher(
      OutboxQueue queue,
      EventPublisherPort publisher,
      ObjectMapper json,
      NotificationProperties properties,
      Environment environment) {
    this.queue = queue;
    this.publisher = publisher;
    this.json = json;
    this.properties = properties;
    this.environment = environment;
  }

  @Scheduled(fixedDelayString = "${campus.notifications.poll-interval-ms:2000}")
  public void scheduled() {
    if (environment.getProperty("campus.notifications.dispatch-enabled", Boolean.class, true))
      dispatch();
  }

  /** No network I/O holds a business transaction; queue lease/ack are separate transactions. */
  public int dispatch() {
    int count = 0;
    for (int i = 0; i < properties.batchSize(); i++) {
      var row = queue.claim();
      if (row == null) break;
      try {
        Map<String, Object> payload =
            json.readValue(row.payload(), new TypeReference<Map<String, Object>>() {});
        publisher.publish(
            new EventPublisherPort.Event(
                row.id(), row.eventType(), row.aggregateId(), row.createdAt(), payload));
        queue.success(row);
      } catch (Exception failure) {
        queue.failure(row);
      }
      count++;
    }
    return count;
  }
}
