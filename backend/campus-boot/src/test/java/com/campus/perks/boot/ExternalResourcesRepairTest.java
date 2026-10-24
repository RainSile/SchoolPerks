package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;

import com.campus.perks.integration.config.*;
import com.campus.perks.integration.port.*;
import com.campus.perks.notification.serviceImpl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit opt-in only: real remote services, minimum test data and exact cleanup. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_EXTERNAL_TEST", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "campus.notifications.dispatch-enabled=false",
      "campus.search.rebuild-enabled=false",
      "campus.trade.scheduler-enabled=false",
      "campus.integrations.ai.max-tokens=256",
      "campus.infrastructure.rabbit.max-attempts=2",
      "campus.infrastructure.rabbit.retry-delay=1s"
    })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("local")
class ExternalResourcesRepairTest {
  @Autowired RateLimitPort limiter;
  @Autowired ConversationStorePort conversations;
  @Autowired AiModelPort model;
  @Autowired VectorSearchPort vectors;
  @Autowired SearchEnginePort engine;
  @Autowired MapServicePort maps;
  @Autowired OutboxPort outbox;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired RabbitOutboxTransport rabbit;
  @Autowired InfrastructureProperties infra;
  @Autowired IntegrationProperties integration;
  @Autowired ElasticsearchProperties elastic;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager manager;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired com.campus.perks.security.service.AccessTokenIssuer issuer;

  @Test
  void redisEnforcesAnAtomicLimit() {
    String key = "external:" + UUID.randomUUID();
    assertThat(limiter.acquire(key, 1, Duration.ofSeconds(30))).isTrue();
    assertThat(limiter.acquire(key, 1, Duration.ofSeconds(30))).isFalse();
  }

  private com.rabbitmq.client.Connection broker() throws Exception {
    var p = infra.rabbit();
    var f = new com.rabbitmq.client.ConnectionFactory();
    f.setHost(p.host());
    f.setPort(p.port());
    f.setUsername(p.username());
    f.setPassword(p.password());
    f.setVirtualHost(p.virtualHost());
    f.setConnectionTimeout((int) p.timeout().toMillis());
    return f.newConnection("campus-external-check");
  }

  private boolean removeTestMessage(String queue, String id) throws Exception {
    try (var connection = broker();
        var channel = connection.createChannel()) {
      // Unrelated messages remain unacknowledged and are returned when this channel closes.
      for (int i = 0; i < 50; i++) {
        var item = channel.basicGet(queue, false);
        if (item == null) return false;
        if (id.equals(item.getProps().getMessageId())) {
          channel.basicAck(item.getEnvelope().getDeliveryTag(), false);
          return true;
        }
      }
      return false;
    }
  }

  @Test
  void rabbitRetriesAFailedConsumerAndRetainsTheEventInItsDeadQueue() throws Exception {
    String id = UUID.randomUUID().toString();
    String queue = infra.rabbit().queue();
    try {
      rabbit.publish(
          new EventPublisherPort.Event(
              id, "EXTERNAL_UNSUPPORTED_TEST", "CHECK:" + id, Instant.now(), Map.of()));
      boolean dead = false;
      for (int i = 0; i < 12; i++) {
        rabbit.consume();
        if (removeTestMessage(queue + ".dead", id)) {
          dead = true;
          break;
        }
        Thread.sleep(400);
      }
      assertThat(dead)
          .as("Consumer failures must be retried and retained after the configured attempt limit")
          .isTrue();
    } finally {
      for (String suffix : List.of("", ".retry-v2", ".dead")) removeTestMessage(queue + suffix, id);
    }
  }

  @Test
  void mongoPersistsPrivateHistoryAndPreventsOlderAttemptsOverwritingIt() {
    String id = UUID.randomUUID().toString(),
        conversation = UUID.randomUUID().toString(),
        owner = "external-" + UUID.randomUUID();
    try {
      var turn =
          new ConversationStorePort.Turn(
              id,
              conversation,
              owner,
              "USER",
              "连接检查",
              "new",
              List.of(),
              List.of(),
              Instant.now(),
              2);
      conversations.save(turn);
      conversations.save(
          new ConversationStorePort.Turn(
              id,
              conversation,
              owner,
              "USER",
              "连接检查",
              "old",
              List.of(),
              List.of(),
              Instant.now(),
              1));
      assertThat(conversations.completed(id, owner, "USER").answer()).isEqualTo("new");
      assertThat(conversations.completed(id, "another-owner", "USER")).isNull();
      assertThat(conversations.history(conversation, owner, "ADMIN", 10)).isEmpty();
    } finally {
      try (var mongo = MongoClients.create(infra.mongo().uri())) {
        mongo
            .getDatabase(infra.mongo().database())
            .getCollection("assistant_turns")
            .deleteOne(Filters.and(Filters.eq("_id", id), Filters.eq("owner", owner)));
      }
    }
  }

  @Test
  void langchainChatEmbeddingAndExistingPineconeIndexWorkTogether() throws Exception {
    var response =
        model.chat(List.of(AiModelPort.Message.text("user", "只回复OK，用于连接验证。")), List.of());
    assertThat(response.text()).isNotBlank();
    float[] embedding = model.embed("campus project integration verification");
    assertThat(embedding).hasSize(1024);
    String id = "CHECK:" + UUID.randomUUID(), scope = integration.pinecone().namespace();
    try {
      vectors.upsert(
          scope,
          id,
          embedding,
          Map.of(
              "visibility",
              "PUBLIC",
              "resource_type",
              "CHECK",
              "resource_id",
              id,
              "source_hash",
              "check",
              "source_version",
              "1",
              "valid_from",
              Instant.now().minusSeconds(60).toString(),
              "valid_until",
              Instant.now().plusSeconds(300).toString()));
      List<VectorSearchPort.Match> matches = List.of();
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      do {
        matches = vectors.search(scope, embedding, 5);
        if (matches.stream().anyMatch(m -> m.documentId().equals(id))) break;
        Thread.sleep(1000);
      } while (System.nanoTime() < deadline);
      assertThat(matches.stream().anyMatch(m -> m.documentId().equals(id))).isTrue();
      assertThat(
              matches.stream()
                  .filter(m -> m.documentId().equals(id))
                  .findFirst()
                  .orElseThrow()
                  .metadata())
          .containsEntry("source_version", "1")
          .containsKey("valid_until");
    } finally {
      vectors.delete(scope, id);
    }
  }

  private long insert(String sql, Object... args) {
    var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
    db.update(
        c -> {
          var p = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
          return p;
        },
        key);
    return key.getKey().longValue();
  }

  @Test
  void mysqlOutboxTravelsThroughRealRabbitAndProjectsToRealEs() throws Exception {
    engine.verifyInstallation();
    long user = 0, school = 0, post = 0;
    String event = UUID.randomUUID().toString();
    try {
      user =
          insert(
              "INSERT INTO campus_user(phone,nickname) VALUES(?,'external-test')",
              "ext-" + UUID.randomUUID().toString().substring(0, 20));
      school = insert("INSERT INTO campus_school(name) VALUES(?)", "external-" + UUID.randomUUID());
      post =
          insert(
              "INSERT INTO campus_post(author_id,school_id,title,body,status,created_at,updated_at)"
                  + " VALUES(?,?,'external draft','not"
                  + " public','DRAFT',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
              user,
              school);
      var payload =
          new EventPublisherPort.Event(
              event,
              "SEARCH_RESOURCE_CHANGED",
              "SEARCH:POST:" + post,
              Instant.now(),
              Map.of("resourceType", "POST", "resourceId", "" + post));
      new TransactionTemplate(manager).executeWithoutResult(s -> outbox.append(payload));
      for (int i = 0; i < 10; i++) {
        dispatcher.dispatch();
        rabbit.consume();
        if (db.queryForObject(
                "SELECT COUNT(*) FROM campus_consumed_event WHERE consumer_name='campus-search' AND"
                    + " event_id=?",
                Integer.class,
                event)
            == 1) break;
        Thread.sleep(300);
      }
      assertThat(
              db.queryForObject(
                  "SELECT status FROM campus_outbox_event WHERE id=?", String.class, event))
          .isEqualTo("PUBLISHED");
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM campus_consumed_event WHERE consumer_name='campus-search'"
                      + " AND event_id=?",
                  Integer.class,
                  event))
          .isEqualTo(1);
      assertThat(
              db.queryForObject(
                  "SELECT visible FROM campus_search_document WHERE resource_type='POST' AND"
                      + " resource_id=?",
                  Boolean.class,
                  post))
          .isFalse();
    } finally {
      if (post != 0) {
        var request =
            HttpRequest.newBuilder(
                    URI.create(elastic.endpoint() + "/" + elastic.index() + "/_doc/POST-" + post))
                .header("Authorization", "ApiKey " + elastic.apiKey())
                .DELETE()
                .build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        db.update(
            "DELETE FROM campus_search_document WHERE resource_type='POST' AND resource_id=?",
            post);
        db.update("DELETE FROM campus_post WHERE id=?", post);
      }
      db.update(
          "DELETE FROM campus_consumed_event WHERE consumer_name='campus-search' AND event_id=?",
          event);
      db.update("DELETE FROM campus_outbox_event WHERE id=?", event);
      if (user != 0) db.update("DELETE FROM campus_user WHERE id=?", user);
      if (school != 0) db.update("DELETE FROM campus_school WHERE id=?", school);
    }
  }

  @Test
  void amapProxyRejectsArbitraryPathsBeforeCallingTheProvider() {
    assertThatThrownBy(() -> maps.proxy("v3/unauthorized/resource", Map.of()))
        .isInstanceOf(com.campus.perks.common.exception.ApplicationException.class)
        .hasMessage("地图资源不存在");
  }

  @Test
  void realAssistantUsesCurrentAccountToolAndPersistsAnIsolatedReply() throws Exception {
    long user =
        insert(
            "INSERT INTO campus_user(phone,nickname,account_type)"
                + " VALUES(?,'external-assistant','USER')",
            "ai-ext-" + UUID.randomUUID().toString().substring(0, 16));
    String conversation = null, sid = UUID.randomUUID().toString();
    var now = Instant.now();
    try {
      db.update(
          "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at) VALUES(?,?,?,?)",
          sid,
          user,
          java.sql.Timestamp.from(now),
          java.sql.Timestamp.from(now.plusSeconds(600)));
      String bearer =
          "Bearer " + issuer.issue("" + user, sid, List.of(), now, now.plusSeconds(300));
      var created =
          mvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                          "/api/v1/assistant/conversations")
                      .header("Authorization", bearer)
                      .contentType("application/json")
                      .content("{}"))
              .andExpect(
                  org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                      .isCreated())
              .andReturn();
      conversation = json.readTree(created.getResponse().getContentAsString()).path("id").asText();
      var reply =
          mvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                          "/api/v1/assistant/conversations/" + conversation + "/messages")
                      .header("Authorization", bearer)
                      .header("Idempotency-Key", UUID.randomUUID().toString())
                      .contentType("application/json")
                      .content(
                          json.writeValueAsString(
                              Map.of("message", "请务必先调用 my_orders 工具查询本人订单，再只告诉我有几笔订单。"))))
              .andExpect(
                  org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                      .isCreated())
              .andReturn();
      var message = json.readTree(reply.getResponse().getContentAsString());
      assertThat(message.path("status").asText()).isEqualTo("COMPLETED");
      assertThat(message.path("reply").asText()).isNotBlank();
      assertThat(message.path("tools").toString()).contains("my_orders");
      String savedConversation = conversation;
      assertThat(conversations.history(savedConversation, "" + user, "USER", 10)).hasSize(1);
      assertThat(conversations.history(savedConversation, "another-user", "USER", 10)).isEmpty();
    } finally {
      if (conversation != null) {
        try (var mongo = MongoClients.create(infra.mongo().uri())) {
          mongo
              .getDatabase(infra.mongo().database())
              .getCollection("assistant_turns")
              .deleteMany(
                  Filters.and(
                      Filters.eq("conversationId", conversation), Filters.eq("owner", "" + user)));
        }
        db.update("DELETE FROM campus_assistant_turn WHERE conversation_id=?", conversation);
        db.update("DELETE FROM campus_assistant_conversation WHERE id=?", conversation);
      }
      db.update("DELETE FROM campus_audit_event WHERE actor_id=?", user);
      db.update("DELETE FROM campus_ai_usage WHERE user_id=?", user);
      db.update("DELETE FROM campus_auth_session WHERE user_id=?", user);
      db.update("DELETE FROM campus_user WHERE id=?", user);
    }
  }
}
