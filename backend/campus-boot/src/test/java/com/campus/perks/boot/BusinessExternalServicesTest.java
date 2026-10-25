package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campus.perks.integration.config.InfrastructureProperties;
import com.campus.perks.integration.port.*;
import com.campus.perks.notification.serviceImpl.RabbitOutboxTransport;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Only the explicitly selected business methods run. No assistant, model, Mongo or vector calls.
 * Uses a fresh Rabbit queue, leaving application queues and messages untouched.
 */
@EnabledIfEnvironmentVariables({
  @EnabledIfEnvironmentVariable(named = "CAMPUS_EXTERNAL_TEST", matches = "true"),
  @EnabledIfEnvironmentVariable(named = "CAMPUS_BUSINESS_EVIDENCE_DIR", matches = ".+"),
  @EnabledIfEnvironmentVariable(
      named = "RABBITMQ_QUEUE",
      matches = "campus\\.business-test\\.[A-Za-z0-9]+")
})
@SpringBootTest(
    properties = {
      "campus.notifications.dispatch-enabled=false",
      "campus.search.rebuild-enabled=false",
      "campus.trade.scheduler-enabled=false",
      "campus.infrastructure.rabbit.max-attempts=2",
      "campus.infrastructure.rabbit.retry-delay=1s"
    })
@ActiveProfiles("local")
class BusinessExternalServicesTest {
  @Autowired SearchEnginePort engine;
  @Autowired RateLimitPort limiter;
  @Autowired MapServicePort maps;
  @Autowired RabbitOutboxTransport rabbit;
  @Autowired InfrastructureProperties infra;

  @BeforeEach
  void requireThisRunsPrivateQueueBeforeAnyPublish() {
    assertThat(infra.rabbit().queue())
        .startsWith("campus.business-test.")
        .isEqualTo(System.getenv("RABBITMQ_QUEUE"));
  }

  @Test
  void redisEnforcesAnAtomicLimit() {
    String key = "business-test:" + UUID.randomUUID();
    assertThat(limiter.acquire(key, 1, Duration.ofSeconds(30))).isTrue();
    assertThat(limiter.acquire(key, 1, Duration.ofSeconds(30))).isFalse();
  }

  @Test
  void amapProxyRejectsArbitraryPathsBeforeCallingTheProvider() {
    assertThatThrownBy(() -> maps.proxy("v3/unauthorized/resource", Map.of()))
        .isInstanceOf(com.campus.perks.common.exception.ApplicationException.class)
        .hasMessage("地图资源不存在");
  }

  @Test
  void rabbitRetriesAFailedConsumerAndRetainsTheEventInItsDeadQueue() throws Exception {
    String id = UUID.randomUUID().toString();
    rabbit.publish(
        new EventPublisherPort.Event(
            id, "BUSINESS_UNSUPPORTED_TEST", "CHECK:" + id, Instant.now(), Map.of()));
    boolean dead = false;
    for (int attempt = 0; attempt < 12 && !dead; attempt++) {
      rabbit.consume();
      try (var connection = broker();
          var channel = connection.createChannel()) {
        var item = channel.basicGet(infra.rabbit().queue() + ".dead", true);
        if (item != null) {
          assertThat(item.getProps().getMessageId()).isEqualTo(id);
          dead = true;
        }
      }
      if (!dead) Thread.sleep(400);
    }
    assertThat(dead).as("Unknown events must retry and reach the private dead queue").isTrue();
  }

  private com.rabbitmq.client.Connection broker() throws Exception {
    var properties = infra.rabbit();
    var factory = new com.rabbitmq.client.ConnectionFactory();
    factory.setHost(properties.host());
    factory.setPort(properties.port());
    factory.setUsername(properties.username());
    factory.setPassword(properties.password());
    factory.setVirtualHost(properties.virtualHost());
    factory.setConnectionTimeout((int) properties.timeout().toMillis());
    return factory.newConnection("campus-business-test");
  }

  @Test
  void businessElasticsearchInstallationAndSearchRespond() {
    engine.verifyInstallation();
    assertThat(engine.candidates("校园", null, null, 2)).isNotNull().hasSizeLessThanOrEqualTo(2);
  }

  @AfterEach
  void deleteOnlyThisRunsTemporaryQueues() throws Exception {
    var properties = infra.rabbit();
    if (!properties.enabled()) return;
    if (!properties.queue().startsWith("campus.business-test.")
        || !properties.queue().equals(System.getenv("RABBITMQ_QUEUE")))
      throw new IllegalStateException("A private test queue is required");
    var factory = new com.rabbitmq.client.ConnectionFactory();
    factory.setHost(properties.host());
    factory.setPort(properties.port());
    factory.setUsername(properties.username());
    factory.setPassword(properties.password());
    factory.setVirtualHost(properties.virtualHost());
    factory.setConnectionTimeout((int) properties.timeout().toMillis());
    try (var connection = factory.newConnection("campus-business-test-cleanup")) {
      for (String suffix : java.util.List.of("", ".retry-v2", ".dead")) {
        var channel = connection.createChannel();
        try {
          try {
            channel.queueDelete(properties.queue() + suffix, false, false);
          } catch (java.io.IOException error) {
            // 404 only means this method did not declare the queue; other failures remain failures.
            Throwable cause = error.getCause();
            if (!(cause instanceof com.rabbitmq.client.ShutdownSignalException signal)
                || !(signal.getReason() instanceof com.rabbitmq.client.AMQP.Channel.Close close)
                || close.getReplyCode() != 404) throw error;
          }
        } finally {
          if (channel.isOpen()) channel.close();
        }
      }
    }
  }
}
