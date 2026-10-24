package com.campus.perks.notification.serviceImpl;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.InfrastructureProperties;
import com.campus.perks.integration.port.EventPublisherPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** SQL outbox -> confirmed durable Rabbit message -> idempotent local consumers. */
@Component
@Primary
@ConditionalOnProperty(
    name = {"campus.auth.enabled", "campus.infrastructure.rabbit.enabled"},
    havingValue = "true")
@EnableConfigurationProperties(InfrastructureProperties.class)
public class RabbitOutboxTransport implements EventPublisherPort, AutoCloseable {
  private final InfrastructureProperties.Rabbit p;
  private final NotificationPublisher receiver;
  private final ObjectMapper json;
  private Connection connection;
  private Channel channel;
  private volatile boolean returned;

  public RabbitOutboxTransport(
      InfrastructureProperties config, NotificationPublisher receiver, ObjectMapper json) {
    this.p = config.rabbit();
    this.receiver = receiver;
    this.json = json;
    if (p.host().isBlank()
        || p.username().isBlank()
        || p.password().isBlank()
        || !p.queue().matches("campus\\.[a-zA-Z0-9_.-]{1,80}")
        || p.batchSize() < 1
        || p.maxAttempts() < 1
        || p.maxQueueLength() < 1)
      throw new IllegalStateException("Invalid RabbitMQ configuration");
  }

  private Channel channel() throws Exception {
    if (channel != null && channel.isOpen()) return channel;
    close();
    var factory = new ConnectionFactory();
    factory.setHost(p.host());
    factory.setPort(p.port());
    factory.setUsername(p.username());
    factory.setPassword(p.password());
    factory.setVirtualHost(p.virtualHost());
    factory.setConnectionTimeout((int) p.timeout().toMillis());
    factory.setHandshakeTimeout((int) p.timeout().toMillis());
    factory.setRequestedHeartbeat(30);
    factory.setAutomaticRecoveryEnabled(false);
    connection = factory.newConnection("campus-outbox");
    channel = connection.createChannel();
    channel.confirmSelect();
    channel.addReturnListener(reply -> returned = true);
    var limit =
        Map.<String, Object>of("x-max-length", p.maxQueueLength(), "x-overflow", "reject-publish");
    channel.queueDeclare(p.queue(), true, false, false, limit);
    channel.queueDeclare(p.queue() + ".dead", true, false, false, limit);
    // Explicit confirmed transfer avoids classic queue TTL/DLX's at-most-once republish window.
    channel.queueDeclare(p.queue() + ".retry-v2", true, false, false, limit);
    return channel;
  }

  private void send(String queue, byte[] bytes, String id, int attempt) throws Exception {
    var c = channel();
    var props =
        new AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .deliveryMode(2)
            .messageId(id)
            .headers(
                Map.of(
                    "campus-attempt",
                    attempt,
                    "campus-not-before",
                    System.currentTimeMillis() + p.retryDelay().toMillis()))
            .build();
    returned = false;
    c.basicPublish("", queue, true, props, bytes);
    c.waitForConfirmsOrDie(p.timeout().toMillis());
    if (returned) throw new IllegalStateException("Broker returned an unroutable event");
  }

  public synchronized void publish(Event event) {
    try {
      send(p.queue(), json.writeValueAsBytes(event), event.eventId(), 0);
    } catch (Exception e) {
      close();
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "MESSAGE_BROKER_UNAVAILABLE", "消息队列暂不可用，事件将由outbox重试");
    }
  }

  @Scheduled(fixedDelayString = "${campus.notifications.poll-interval-ms:2000}")
  public synchronized void consume() {
    try {
      var c = channel();
      for (int i = 0; i < p.batchSize(); i++) {
        var retry = c.basicGet(p.queue() + ".retry-v2", false);
        if (retry == null) break;
        var h = retry.getProps().getHeaders();
        long due = h != null && h.get("campus-not-before") instanceof Number n ? n.longValue() : 0;
        if (due > System.currentTimeMillis()) {
          c.basicNack(retry.getEnvelope().getDeliveryTag(), false, true);
          break;
        }
        int attempt = h != null && h.get("campus-attempt") instanceof Number n ? n.intValue() : 1;
        send(p.queue(), retry.getBody(), retry.getProps().getMessageId(), attempt);
        c.basicAck(retry.getEnvelope().getDeliveryTag(), false);
      }
      for (int i = 0; i < p.batchSize(); i++) {
        var item = c.basicGet(p.queue(), false);
        if (item == null) return;
        if (item.getBody().length > 1024 * 1024) {
          send(
              p.queue() + ".dead", item.getBody(), item.getProps().getMessageId(), p.maxAttempts());
          c.basicAck(item.getEnvelope().getDeliveryTag(), false);
          continue;
        }
        try {
          receiver.publish(json.readValue(item.getBody(), Event.class));
          c.basicAck(item.getEnvelope().getDeliveryTag(), false);
        } catch (Exception failed) {
          var headers = item.getProps().getHeaders();
          int attempt =
              headers != null && headers.get("campus-attempt") instanceof Number n
                  ? n.intValue() + 1
                  : 1;
          send(
              attempt >= p.maxAttempts() ? p.queue() + ".dead" : p.queue() + ".retry-v2",
              item.getBody(),
              item.getProps().getMessageId(),
              attempt);
          c.basicAck(item.getEnvelope().getDeliveryTag(), false);
        }
      }
    } catch (Exception unavailable) {
      close();
    }
  }

  @jakarta.annotation.PreDestroy
  public synchronized void close() {
    if (channel != null) {
      try {
        channel.close();
      } catch (Exception ignored) {
      }
      channel = null;
    }
    if (connection != null) {
      try {
        connection.close();
      } catch (Exception ignored) {
      }
      connection = null;
    }
  }
}
