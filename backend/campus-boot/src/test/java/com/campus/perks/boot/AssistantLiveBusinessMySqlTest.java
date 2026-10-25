package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;

import com.campus.perks.boot.support.BusinessDatasetCleanup;
import com.campus.perks.integration.config.InfrastructureProperties;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.fasterxml.jackson.databind.*;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

/** Explicit opt-in. Real provider, Redis, MongoDB, HTTP controllers and MySQL; no model mocks. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_AI_LIVE_TEST", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.assistant.enabled=true",
      "campus.notifications.dispatch-enabled=false",
      "campus.search.rebuild-enabled=false",
      "campus.trade.scheduler-enabled=false",
      "campus.assistant.requests-per-minute=100",
      "campus.assistant.budget.daily-chat-calls=1000",
      "campus.assistant.budget.daily-embedding-calls=1000",
      "campus.integrations.ai.max-tokens=2048"
    })
@ActiveProfiles("local")
class AssistantLiveBusinessMySqlTest {
  @Autowired JdbcTemplate db;
  @Autowired DataSource source;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenIssuer issuer;
  @Autowired InfrastructureProperties infra;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.campus.perks.assistant.serviceImpl.AssistantToolRegistry toolRegistry;

  @LocalServerPort int port;
  final List<Long> users = new ArrayList<>(), roles = new ArrayList<>();
  final List<Map<String, Object>> traces = new ArrayList<>();
  long school, student, merchant, otherMerchant, admin, store, otherStore, offer;
  String title = "assistant-live-" + UUID.randomUUID();
  final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private static String secret() {
    byte[] value = new byte[32];
    new java.security.SecureRandom().nextBytes(value);
    return Base64.getEncoder().encodeToString(value);
  }

  static final String JWT = secret(), OTP = secret(), IDENTITY = secret();

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry registry) {
    registry.add("campus.security.jwt.secret-base64", () -> JWT);
    registry.add("campus.auth.code-hash-secret-base64", () -> OTP);
    registry.add("campus.auth.identity-key-base64", () -> IDENTITY);
  }

  long insert(String sql, Object... arguments) {
    var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
    db.update(
        connection -> {
          var statement =
              connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
          return statement;
        },
        key);
    return key.getKey().longValue();
  }

  long account(String portal) {
    long id =
        insert(
            "INSERT INTO campus_user(phone,nickname,account_type)"
                + " VALUES(?,'assistant-live-fixture',?)",
            "ai-" + UUID.randomUUID().toString().substring(0, 20),
            portal);
    users.add(id);
    return id;
  }

  long store(long owner) {
    String file = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at,completed_at)"
            + " VALUES(?,?,'MERCHANT_QUALIFICATION','fixture.pdf','application/pdf',64,?,?,'READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
            + " 1 HOUR),UTC_TIMESTAMP(3))",
        file,
        owner,
        "assistant-fixture/pending/" + file,
        "assistant-fixture/sealed/" + file);
    long id =
        insert(
            "INSERT INTO"
                + " campus_merchant(owner_id,school_id,name,category,address,status,qualification_file_id)"
                + " VALUES(?,?,?,'DINING','assistant fixture','APPROVED',?)",
            owner,
            school,
            "assistant-live-store-" + owner,
            file);
    db.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'OWNER')",
        id,
        owner);
    return id;
  }

  @BeforeEach
  void fixtures() {
    org.mockito.Mockito.doAnswer(
            invocation -> {
              String name = invocation.getArgument(0), arguments = invocation.getArgument(1);
              try {
                String result = (String) invocation.callRealMethod();
                traces.add(Map.of("tool", name, "arguments", arguments, "result", result));
                return result;
              } catch (Exception error) {
                traces.add(
                    Map.of(
                        "tool",
                        name,
                        "error",
                        error instanceof com.campus.perks.common.exception.ApplicationException safe
                            ? safe.code()
                            : "TOOL_FAILURE"));
                throw error;
              }
            })
        .when(toolRegistry)
        .execute(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    school =
        insert("INSERT INTO campus_school(name) VALUES(?)", "assistant-live-" + UUID.randomUUID());
    student = account("USER");
    merchant = account("MERCHANT");
    otherMerchant = account("MERCHANT");
    admin = account("ADMIN");
    store = store(merchant);
    otherStore = store(otherMerchant);
    long role =
        insert(
            "INSERT INTO campus_role(code,name,account_type) VALUES(?,'assistant live"
                + " platform','ADMIN')",
            "ai-" + UUID.randomUUID());
    roles.add(role);
    db.update(
        "INSERT INTO campus_role_permission(role_id,permission_id) SELECT ?,id FROM"
            + " campus_permission WHERE code='business:manage'",
        role);
    db.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", admin, role);
    offer =
        insert(
            "INSERT INTO"
                + " campus_trade_offer(merchant_id,title,description,kind,price,stock_total,stock_available,starts_at,ends_at,status,created_by,created_at)"
                + " VALUES(?,'assistant live"
                + " package','fixture','REGULAR',9.90,5,5,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1"
                + " HOUR),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2"
                + " DAY),'PUBLISHED',?,UTC_TIMESTAMP(3))",
            store,
            merchant);
    String evidence = System.getenv("CAMPUS_ASSISTANT_EVIDENCE_DIR");
    if (evidence != null)
      try {
        java.nio.file.Files.writeString(
            java.nio.file.Path.of(evidence, "live-fixtures.json"),
            json.writeValueAsString(Map.of("users", users, "roles", roles, "school", school)));
      } catch (Exception error) {
        throw new IllegalStateException(error);
      }
  }

  String token(long owner) {
    var now = Instant.now();
    String sid = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version) SELECT"
            + " ?,id,?,?,access_version FROM campus_user WHERE id=?",
        sid,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now.plusSeconds(3600)),
        owner);
    return "Bearer " + issuer.issue("" + owner, sid, List.of(), now, now.plusSeconds(1800));
  }

  JsonNode call(String method, String path, String auth, Object body) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
            .timeout(Duration.ofMinutes(2))
            .header("Authorization", auth)
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(method + " " + path).isBetween(200, 299);
    return json.readTree(response.body());
  }

  JsonNode ask(String portal, String auth, String conversation, String question) throws Exception {
    if (conversation == null)
      conversation = call("POST", "/assistant/conversations", auth, Map.of()).path("id").asText();
    String key = UUID.randomUUID().toString();
    long start = System.nanoTime(), first = 0;
    int deltas = 0;
    List<String> tools = new ArrayList<>();
    JsonNode result = null;
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + "/api/v1/assistant/conversations/"
                        + conversation
                        + "/message-streams"))
            .timeout(Duration.ofMinutes(10))
            .header("Authorization", auth)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(Map.of("message", question, "allowWrites", true))))
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    assertThat(response.statusCode()).isEqualTo(200);
    try (var reader =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
      String event = "";
      for (String line; (line = reader.readLine()) != null; ) {
        if (line.startsWith("event:")) event = line.substring(6).strip();
        if (!line.startsWith("data:")) continue;
        var data = json.readTree(line.substring(5));
        if (event.equals("delta")) {
          deltas++;
          if (first == 0) first = System.nanoTime();
        }
        if (event.equals("tool")) tools.add(data.path("name").asText());
        if (event.equals("error")) {
          traces.add(Map.of("portal", portal, "error", data, "tools", tools));
          throw new AssertionError(
              "Live assistant error: "
                  + data.path("code").asText()
                  + " "
                  + data.path("detail").asText());
        }
        if (event.equals("completed")) result = data;
      }
    }
    assertThat(result).as("durable completed event").isNotNull();
    assertThat(result.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(deltas).as("real provider partial responses").isGreaterThan(1);
    traces.add(
        Map.of(
            "portal",
            portal,
            "conversation",
            conversation,
            "message",
            result.path("id").asText(),
            "deltaEvents",
            deltas,
            "firstDeltaMs",
            (first - start) / 1000000,
            "durationMs",
            (System.nanoTime() - start) / 1000000,
            "tools",
            tools,
            "reply",
            result.path("reply").asText()));
    return result;
  }

  @Test
  void realModelsCompleteMerchantAdminStudentBusinessChains() throws Exception {
    String merchantAuth = token(merchant), adminAuth = token(admin), studentAuth = token(student);
    String from = Instant.now().minusSeconds(60).toString(),
        until = Instant.now().plusSeconds(86400).toString();
    ask(
        "MERCHANT",
        merchantAuth,
        null,
        "请实际调用业务工具，在我的门店"
            + store
            + "创建一张优惠券草稿并提交审核，再查询确认待审核状态。标题为"
            + title
            + "，类型AMOUNT_OFF，满10减2，总库存10，有效期"
            + from
            + "到"
            + until
            + "，campusOnly=false，description=校园测试优惠。不要发布或重复创建。先查API目录和schema，必须按返回的真实ID及version完成操作。最后用Markdown表格说明结果。");
    long coupon =
        db.queryForObject(
            "SELECT id FROM campus_coupon WHERE merchant_id=? AND title=?",
            Long.class,
            store,
            title);
    assertThat(
            db.queryForObject(
                "SELECT review_status FROM campus_coupon WHERE id=?", String.class, coupon))
        .isEqualTo("PENDING");
    ask(
        "ADMIN",
        adminAuth,
        null,
        "请实际完成平台跨端业务：查找门店"
            + store
            + "的优惠券"
            + coupon
            + "待审请求，审核通过，理由=独立测试审核；然后通过商家业务API发布此券（不是只批准），理由=独立测试发布，最后查询确认PUBLISHED。请先查目录和schema，使用真实version，不要修改其他资源。用Markdown表格说明实际结果。");
    assertThat(
            db.queryForObject("SELECT status FROM campus_coupon WHERE id=?", String.class, coupon))
        .isEqualTo("PUBLISHED");
    var studentReply =
        ask(
            "USER",
            studentAuth,
            null,
            "请实际领取优惠券"
                + coupon
                + "，然后查询我的券包确认已领取，只处理这一张。不要访问商家或管理端业务。请先查API目录和schema；最后使用Markdown表格展示已核实的结果。");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_coupon_claim WHERE coupon_id=? AND user_id=?",
                Integer.class,
                coupon,
                student))
        .isEqualTo(1);
    ask(
        "USER",
        studentAuth,
        studentReply.path("conversationId").asText(),
        "继续实际操作：先查询套餐"
            + offer
            + "，为当前账号创建一笔该套餐的普通订单，然后立即取消这笔新订单，最后查询确认取消成功。不要支付，不要使用优惠券，不要影响其他订单。先查API目录和schema并使用真实ID/version；用Markdown表格展示结果。");
    assertThat(
            db.queryForList(
                "SELECT status FROM campus_order WHERE user_id=?", String.class, student))
        .containsExactly("CANCELLED");
    assertThat(
            db.queryForObject(
                "SELECT stock_available FROM campus_trade_offer WHERE id=?", Integer.class, offer))
        .isEqualTo(5);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_coupon WHERE merchant_id=?",
                Integer.class,
                otherStore))
        .isZero();
    var history =
        call(
            "GET",
            "/assistant/conversations/"
                + studentReply.path("conversationId").asText()
                + "/messages",
            studentAuth,
            null);
    assertThat(history.size()).isEqualTo(2);
  }

  @AfterEach
  void cleanup() throws Exception {
    String destination = System.getenv("CAMPUS_ASSISTANT_EVIDENCE_DIR");
    if (destination != null) {
      java.nio.file.Files.writeString(
          java.nio.file.Path.of(destination, "live-traces.json"),
          json.writerWithDefaultPrettyPrinter().writeValueAsString(traces));
      var actions =
          db.queryForList(
              "SELECT e.operation_id,e.ordinal,e.status,e.response_body FROM"
                  + " campus_assistant_tool_execution e JOIN campus_assistant_turn t ON"
                  + " t.id=e.turn_id JOIN campus_assistant_conversation c ON c.id=t.conversation_id"
                  + " WHERE c.owner_id IN ("
                  + String.join(",", Collections.nCopies(users.size(), "?"))
                  + ")",
              users.toArray());
      java.nio.file.Files.writeString(
          java.nio.file.Path.of(destination, "live-actions.json"),
          json.writerWithDefaultPrettyPrinter().writeValueAsString(actions));
    }
    try (var mongo = MongoClients.create(infra.mongo().uri())) {
      mongo
          .getDatabase(infra.mongo().database())
          .getCollection("assistant_turns")
          .deleteMany(Filters.in("owner", users.stream().map(Object::toString).toList()));
    } finally {
      new BusinessDatasetCleanup(
              source,
              Map.of("campus_user", users, "campus_school", List.of(school), "campus_role", roles))
          .clean();
    }
  }
}
