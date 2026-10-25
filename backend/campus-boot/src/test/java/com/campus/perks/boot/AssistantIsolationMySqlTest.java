package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.assistant.enabled=true",
      "campus.notifications.dispatch-enabled=false",
      "campus.search.rebuild-enabled=false",
      "campus.trade.scheduler-enabled=false"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
class AssistantIsolationMySqlTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenIssuer issuer;
  @Autowired PublicContentPort content;
  @Autowired com.campus.perks.assistant.serviceImpl.AssistantApiCatalog catalog;
  @Autowired javax.sql.DataSource dataSource;
  @org.springframework.boot.test.web.server.LocalServerPort int port;
  @MockitoBean AiModelPort model;
  @MockitoBean ConversationStorePort store;
  @MockitoBean RateLimitPort limit;
  @MockitoBean VectorSearchPort vectors;
  final Map<String, ConversationStorePort.Turn> saved = new ConcurrentHashMap<>();
  final List<Long> users = new ArrayList<>(), roles = new ArrayList<>();
  long user, other, admin, school;
  final List<Long> posts = new ArrayList<>();

  private static String secret() {
    byte[] b = new byte[32];
    new java.security.SecureRandom().nextBytes(b);
    return Base64.getEncoder().encodeToString(b);
  }

  private static final String JWT = secret(), OTP = secret(), IDENTITY = secret();

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) {
    r.add("campus.security.jwt.secret-base64", () -> JWT);
    r.add("campus.auth.code-hash-secret-base64", () -> OTP);
    r.add("campus.auth.identity-key-base64", () -> IDENTITY);
  }

  long insert(String sql, Object... args) {
    var k = new org.springframework.jdbc.support.GeneratedKeyHolder();
    db.update(
        c -> {
          var p = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
          return p;
        },
        k);
    return k.getKey().longValue();
  }

  long account(String type) {
    long id =
        insert(
            "INSERT INTO campus_user(phone,nickname,account_type) VALUES(?,'assistant-test',?)",
            "ai-" + UUID.randomUUID().toString().substring(0, 20),
            type);
    users.add(id);
    return id;
  }

  @BeforeEach
  void setup() {
    user = account("USER");
    other = account("USER");
    admin = account("ADMIN");
    school = insert("INSERT INTO campus_school(name) VALUES(?)", "ai-" + UUID.randomUUID());
    when(model.chat(anyList(), anyList())).thenReturn(new AiModelPort.Response("测试回答", List.of()));
    when(model.embed(anyString())).thenReturn(new float[1024]);
    when(vectors.search(anyString(), any(), anyInt())).thenReturn(List.of());
    when(limit.acquire(anyString(), anyInt(), any())).thenReturn(true);
    when(store.completed(anyString(), anyString(), anyString()))
        .thenAnswer(
            i -> {
              var t = saved.get(i.getArgument(0));
              return t != null
                      && t.owner().equals(i.getArgument(1))
                      && t.portal().equals(i.getArgument(2))
                  ? t
                  : null;
            });
    when(store.history(anyString(), anyString(), anyString(), anyInt()))
        .thenAnswer(
            i ->
                saved.values().stream()
                    .filter(
                        t ->
                            t.conversationId().equals(i.getArgument(0))
                                && t.owner().equals(i.getArgument(1))
                                && t.portal().equals(i.getArgument(2)))
                    .sorted(Comparator.comparing(ConversationStorePort.Turn::createdAt))
                    .toList());
    doAnswer(
            i -> {
              ConversationStorePort.Turn t = i.getArgument(0);
              saved.compute(
                  t.id(), (k, old) -> old == null || old.attempt() <= t.attempt() ? t : old);
              return null;
            })
        .when(store)
        .save(any());
  }

  String token(long user) {
    var now = Instant.now();
    String sid = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version) SELECT"
            + " ?,id,?,?,access_version FROM campus_user WHERE id=?",
        sid,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now.plusSeconds(3600)),
        user);
    return "Bearer "
        + issuer.issue("" + user, sid, List.of("rbac:manage"), now, now.plusSeconds(600));
  }

  ResultActions call(String method, String path, String auth, Object input, String key)
      throws Exception {
    var r =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/api/v1/assistant" + path)
            .header("Authorization", auth);
    if (input != null)
      r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input));
    if (key != null) r.header("Idempotency-Key", key);
    return mvc.perform(r);
  }

  JsonNode body(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  String create(String auth) throws Exception {
    return body(call("POST", "/conversations", auth, Map.of(), null)
            .andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  ResultActions send(String c, String auth, String text, String key) throws Exception {
    return call("POST", "/conversations/" + c + "/messages", auth, Map.of("message", text), key);
  }

  @Test
  void conversationsArePrivateAndIdempotentMessagesDoNotBillTwice() throws Exception {
    String auth = token(user), c = create(auth), key = UUID.randomUUID().toString();
    var message = body(send(c, auth, "你好", key).andExpect(status().isCreated()));
    send(c, auth, "你好", key)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(message.get("id").asText()));
    verify(model, times(1)).chat(anyList(), anyList());
    send(c, auth, "不同问题", key).andExpect(status().isConflict());
    call("GET", "/conversations/" + c + "/messages", token(other), null, null)
        .andExpect(status().isNotFound());
    call("GET", "/conversations/" + c + "/messages", auth, null, null)
        .andExpect(jsonPath("$[0].reply").value("测试回答"));
  }

  @Test
  void userToolsCallOnlyAuthorizedRealBackendResources() throws Exception {
    when(model.chat(anyList(), anyList()))
        .thenReturn(
            new AiModelPort.Response(
                "", List.of(new AiModelPort.ToolCall("call-1", "my_orders", "{}"))),
            new AiModelPort.Response("你暂时没有订单", List.of()));
    String auth = token(user), c = create(auth);
    send(c, auth, "我的订单", UUID.randomUUID().toString())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tools[0]").value("my_orders"));
    var captor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(model, times(2)).chat(captor.capture(), anyList());
    assertThat(captor.getAllValues().get(1).toString())
        .contains("\"total\":0")
        .doesNotContain("Bearer ", "accessToken", "refreshToken");
  }

  @Test
  void modelCannotSelectAnotherPortalToolOrAnArbitraryUrl() throws Exception {
    when(model.chat(anyList(), anyList()))
        .thenReturn(
            new AiModelPort.Response(
                "",
                List.of(
                    new AiModelPort.ToolCall(
                        "forbidden", "store_orders", "{\"merchantId\":\"1\"}"))));
    String auth = token(user), c = create(auth);
    send(c, auth, "查看门店", UUID.randomUUID().toString())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TOOL_NOT_ALLOWED"));
    verify(store, never()).save(any());
  }

  @Test
  void adminToolsRequireTheSpecificLivePermission() throws Exception {
    long role =
        insert(
            "INSERT INTO campus_role(code,name,account_type) VALUES(?,'ai-reviewer','ADMIN')",
            "AI_" + UUID.randomUUID());
    roles.add(role);
    db.update(
        "INSERT INTO campus_role_permission(role_id,permission_id) SELECT ?,id FROM"
            + " campus_permission WHERE code='community:moderate'",
        role);
    db.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", admin, role);
    var cap =
        body(call("GET", "/capabilities", token(admin), null, null).andExpect(status().isOk()));
    assertThat(cap.get("tools").toString())
        .contains("pending_community_reviews")
        .doesNotContain("my_orders", "list_roles", "pending_merchant_reviews");
  }

  @Test
  void revokedSessionCannotReceiveOrPersistAnInFlightAnswer() throws Exception {
    String auth = token(user), c = create(auth);
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            i -> {
              db.update("UPDATE campus_user SET access_version=access_version+1 WHERE id=?", user);
              return new AiModelPort.Response("不应保存", List.of());
            });
    send(c, auth, "你好", UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
    verify(store, never()).save(any());
  }

  @Test
  void hiddenRagSourcesAreDiscardedBeforeReturningTheAnswer() throws Exception {
    long post =
        insert(
            "INSERT INTO"
                + " campus_post(author_id,school_id,title,body,status,created_at,updated_at,published_at)"
                + " VALUES(?,?,'公开参考','午餐优惠','PUBLISHED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            user,
            school);
    posts.add(post);
    var source = content.find(List.of(new PublicContentPort.Key("POST", "" + post))).get(0);
    when(vectors.search(anyString(), any(), anyInt()))
        .thenReturn(
            List.of(
                new VectorSearchPort.Match(
                    "POST:" + post,
                    1,
                    Map.of(
                        "resource_type",
                        "POST",
                        "resource_id",
                        "" + post,
                        "source_hash",
                        source.hash()))));
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            i -> {
              db.update("UPDATE campus_post SET status='HIDDEN' WHERE id=?", post);
              return new AiModelPort.Response("旧内容", List.of());
            });
    String auth = token(user), c = create(auth);
    send(c, auth, "午餐", UUID.randomUUID().toString())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSISTANT_SOURCE_CHANGED"));
    verify(store, never()).save(any());
  }

  @Test
  void remoteRetrievalFailureFallsBackWithoutInventingSources() throws Exception {
    when(vectors.search(anyString(), any(), anyInt()))
        .thenThrow(
            new com.campus.perks.common.exception.ApplicationException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "VECTOR_UNAVAILABLE",
                "检索暂不可用"));
    String auth = token(user), conversation = create(auth);
    send(conversation, auth, "你好", UUID.randomUUID().toString())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sources").isEmpty());
    verify(model, times(1)).chat(anyList(), anyList());
  }

  @AfterEach
  void cleanup() {
    try {
      new com.campus.perks.boot.support.BusinessDatasetCleanup(
              dataSource,
              Map.of("campus_user", users, "campus_school", List.of(school), "campus_role", roles))
          .clean();
    } catch (Exception error) {
      throw new IllegalStateException("Exact assistant fixture cleanup failed", error);
    }
    saved.clear();
  }

  String operation(String method, String path) {
    return catalog.all().stream()
        .filter(a -> a.method().equals(method) && a.path().equals("/api/v1" + path))
        .findFirst()
        .orElseThrow()
        .id();
  }

  AiModelPort.Response api(String id, Map<String, Object> path, Map<String, Object> body)
      throws Exception {
    return new AiModelPort.Response(
        "",
        List.of(
            new AiModelPort.ToolCall(
                UUID.randomUUID().toString(),
                "execute_business_api",
                json.writeValueAsString(
                    Map.of(
                        "operationId",
                        id,
                        "path",
                        json.writeValueAsString(path),
                        "body",
                        json.writeValueAsString(body))))));
  }

  long grant(long owner, String permission) {
    long role =
        insert(
            "INSERT INTO campus_role(code,name,account_type) VALUES(?,'assistant fixture','ADMIN')",
            "ai-" + UUID.randomUUID());
    roles.add(role);
    db.update(
        "INSERT INTO campus_role_permission(role_id,permission_id) SELECT ?,id FROM"
            + " campus_permission WHERE code=?",
        role,
        permission);
    db.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", owner, role);
    return role;
  }

  Map<String, Object> postInput() {
    return Map.of(
        "schoolId",
        "" + school,
        "content",
        Map.of("title", "assistant fixture draft", "body", "校园优惠测试草稿", "imageIds", List.of()));
  }

  @Test
  void multiStepWritesUseRealApisAndReplayWithoutDuplicatingData() throws Exception {
    var requests = new java.util.concurrent.atomic.AtomicInteger();
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            invocation -> {
              if (requests.getAndIncrement() == 0)
                return api(operation("POST", "/posts"), Map.of(), postInput());
              if (requests.get() == 2) {
                List<AiModelPort.Message> messages = invocation.getArgument(0);
                var result = json.readTree(messages.get(messages.size() - 1).text());
                assertThat(result.path("status").asInt()).isEqualTo(201);
                return api(
                    operation("GET", "/users/me/posts/{id}"),
                    Map.of("id", result.path("data").path("id").asText()),
                    Map.of());
              }
              return new AiModelPort.Response("## 已创建\n\n草稿已通过实时查询确认。", List.of());
            });
    String auth = token(user), conversation = create(auth), key = UUID.randomUUID().toString();
    var input = Map.of("message", "创建校园优惠草稿并读取确认", "allowWrites", true);
    var row =
        body(
            call("POST", "/conversations/" + conversation + "/messages", auth, input, key)
                .andExpect(status().isCreated()));
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_post WHERE author_id=?", Integer.class, user))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_assistant_tool_execution WHERE turn_id=? AND"
                    + " status='COMPLETED'",
                Integer.class,
                row.path("id").asText()))
        .isEqualTo(1);
    call("POST", "/conversations/" + conversation + "/messages", auth, input, key)
        .andExpect(status().isOk());
    assertThat(requests.get()).isEqualTo(3);
    call(
            "POST",
            "/conversations/" + conversation + "/messages",
            auth,
            Map.of("message", input.get("message"), "allowWrites", false),
            key)
        .andExpect(status().isConflict());
    grant(admin, "business:manage");
    for (String stranger : List.of(token(other), token(admin))) {
      call("GET", "/conversations/" + conversation + "/messages", stranger, null, null)
          .andExpect(status().isNotFound());
      call(
              "GET",
              "/conversations/" + conversation + "/messages/" + row.path("id").asText(),
              stranger,
              null,
              null)
          .andExpect(status().isNotFound());
      call(
              "POST",
              "/conversations/" + conversation + "/messages",
              stranger,
              input,
              UUID.randomUUID().toString())
          .andExpect(status().isNotFound());
      call(
              "POST",
              "/conversations/" + conversation + "/message-streams",
              stranger,
              input,
              UUID.randomUUID().toString())
          .andExpect(status().isNotFound());
    }
  }

  @Test
  void writesRequireConsentAndUnknownOrCrossPortalOperationsCannotRun() throws Exception {
    when(model.chat(anyList(), anyList()))
        .thenAnswer(i -> api(operation("POST", "/posts"), Map.of(), postInput()));
    String auth = token(user), conversation = create(auth);
    send(conversation, auth, "创建草稿", UUID.randomUUID().toString())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ASSISTANT_WRITE_CONSENT_REQUIRED"));
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_post WHERE author_id=?", Integer.class, user))
        .isZero();
    when(model.chat(anyList(), anyList()))
        .thenAnswer(i -> api(operation("GET", "/admin/roles"), Map.of(), Map.of()));
    send(conversation, auth, "越权角色查询", UUID.randomUUID().toString())
        .andExpect(status().isForbidden());
    when(model.chat(anyList(), anyList()))
        .thenAnswer(i -> api("https://example.com/evil", Map.of(), Map.of()));
    send(conversation, auth, "外部地址调用", UUID.randomUUID().toString())
        .andExpect(status().isForbidden());
  }

  @Test
  void catalogReflectsExactPortalAndDatabaseBackedPlatformGrants() throws Exception {
    assertThat(
            com.campus.perks.assistant.serviceImpl.AssistantApiPolicy.portal(
                "/api/v1/merchants/{id}", "GET"))
        .isEqualTo("USER");
    assertThat(
            com.campus.perks.assistant.serviceImpl.AssistantApiPolicy.portal(
                "/api/v1/merchants/{id}", "PATCH"))
        .isEqualTo("MERCHANT");
    assertThat(
            com.campus.perks.assistant.serviceImpl.AssistantApiPolicy.portal(
                "/api/v1/new-unclassified-business", "POST"))
        .isEqualTo("UNCLASSIFIED");
    long merchant = account("MERCHANT");
    var student = body(call("GET", "/business-apis", token(user), null, null));
    var stores = body(call("GET", "/business-apis", token(merchant), null, null));
    assertThat(student.findValuesAsText("portal")).doesNotContain("MERCHANT", "ADMIN");
    assertThat(stores.findValuesAsText("portal")).doesNotContain("USER", "ADMIN");
    assertThat(stores.findValuesAsText("path"))
        .contains("/api/v1/coupon-review-requests/{id}", "/api/v1/merchants/{merchantId}/coupons");
    grant(admin, "business:manage");
    var platform = body(call("GET", "/business-apis", token(admin), null, null));
    assertThat(platform.size()).isEqualTo(catalog.all().size());
    assertThat(platform.findValuesAsText("portal")).contains("USER", "MERCHANT", "ADMIN");
    assertThat(platform.findValuesAsText("path"))
        .noneMatch(
            path ->
                path.contains("password")
                    || path.contains("message-streams")
                    || path.contains("_AMapService"));
    var post =
        catalog.all().stream()
            .filter(a -> a.path().equals("/api/v1/posts") && a.method().equals("POST"))
            .findFirst()
            .orElseThrow();
    assertThat(json.writeValueAsString(post.body())).contains("schoolId", "content", "imageIds");
    String destination = System.getenv("CAMPUS_ASSISTANT_INVENTORY");
    if (destination != null && !destination.isBlank())
      java.nio.file.Files.writeString(
          java.nio.file.Path.of(destination),
          json.writerWithDefaultPrettyPrinter().writeValueAsString(catalog.all()));
  }

  @Test
  void streamDeliversChunksBeforeCompletionAndKeepsPrivateHistory() throws Exception {
    var completed = new java.util.concurrent.atomic.AtomicBoolean();
    when(model.stream(anyList(), anyList(), any()))
        .thenAnswer(
            invocation -> {
              java.util.function.Consumer<String> delta = invocation.getArgument(2);
              delta.accept("## 校园\n\n");
              Thread.sleep(350);
              delta.accept("**优惠**已核实");
              Thread.sleep(350);
              completed.set(true);
              return new AiModelPort.Response("## 校园\n\n**优惠**已核实", List.of());
            });
    String auth = token(user), conversation = create(auth);
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(
                    "http://127.0.0.1:"
                        + port
                        + "/api/v1/assistant/conversations/"
                        + conversation
                        + "/message-streams"))
            .header("Authorization", auth)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"message\":\"流式测试\"}"))
            .build();
    var response =
        java.net.http.HttpClient.newHttpClient()
            .send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("text/event-stream");
    StringBuilder stream = new StringBuilder();
    boolean observed = false;
    try (var lines =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
      for (String line; (line = lines.readLine()) != null; ) {
        stream.append(line).append('\n');
        if (line.startsWith("event:delta") && !observed) {
          observed = true;
          assertThat(completed).isFalse();
        }
      }
    }
    assertThat(observed).isTrue();
    assertThat(stream.toString()).contains("event:completed").doesNotContain("event:error");
    call("GET", "/conversations/" + conversation + "/messages", auth, null, null)
        .andExpect(jsonPath("$[0].reply").value("## 校园\n\n**优惠**已核实"));
  }

  long storeFor(long owner) {
    String file = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at,completed_at)"
            + " VALUES(?,?,'MERCHANT_QUALIFICATION','fixture.pdf','application/pdf',64,?,?,'READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
            + " 1 HOUR),UTC_TIMESTAMP(3))",
        file,
        owner,
        "assistant/pending/" + file,
        "assistant/sealed/" + file);
    long store =
        insert(
            "INSERT INTO"
                + " campus_merchant(owner_id,school_id,name,category,address,status,qualification_file_id)"
                + " VALUES(?,?,'assistant fixture','DINING','fixture','APPROVED',?)",
            owner,
            school,
            file);
    db.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'OWNER')",
        store,
        owner);
    return store;
  }

  @Test
  void merchantOwnershipRemainsEnforcedAndPlatformAdminUsesAllThreeRealApiPortals()
      throws Exception {
    long merchant = account("MERCHANT"), otherMerchant = account("MERCHANT");
    storeFor(merchant);
    long foreignStore = storeFor(otherMerchant);
    var index = new java.util.concurrent.atomic.AtomicInteger();
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            invocation -> {
              if (index.getAndIncrement() == 0)
                return api(
                    operation("GET", "/merchants/{merchantId}/coupons"),
                    Map.of("merchantId", "" + foreignStore),
                    Map.of());
              List<AiModelPort.Message> messages = invocation.getArgument(0);
              assertThat(
                      json.readTree(messages.get(messages.size() - 1).text())
                          .path("status")
                          .asInt())
                  .isIn(403, 404);
              return new AiModelPort.Response("此门店不可访问", List.of());
            });
    String merchantAuth = token(merchant);
    send(create(merchantAuth), merchantAuth, "别的门店优惠", UUID.randomUUID().toString())
        .andExpect(status().isCreated());
    grant(admin, "business:manage");
    index.set(0);
    var coupon =
        Map.<String, Object>of(
            "title",
            "platform fixture coupon",
            "couponType",
            "AMOUNT_OFF",
            "rules",
            Map.of("threshold", 10, "amountOff", 2),
            "totalStock",
            5,
            "validFrom",
            Instant.now().minusSeconds(60).toString(),
            "validUntil",
            Instant.now().plusSeconds(3600).toString(),
            "campusOnly",
            false,
            "description",
            "fixture");
    doAnswer(
            invocation -> {
              int step = index.getAndIncrement();
              if (step == 0)
                return api(
                    operation("POST", "/merchants/{merchantId}/coupons"),
                    Map.of("merchantId", "" + foreignStore),
                    coupon);
              if (step == 1) {
                List<AiModelPort.Message> messages = invocation.getArgument(0);
                assertThat(
                        json.readTree(messages.get(messages.size() - 1).text())
                            .path("status")
                            .asInt())
                    .isEqualTo(201);
                return api(operation("POST", "/posts"), Map.of(), postInput());
              }
              if (step == 2) return api(operation("GET", "/admin/roles"), Map.of(), Map.of());
              return new AiModelPort.Response("三端操作已完成", List.of());
            })
        .when(model)
        .chat(anyList(), anyList());
    String adminAuth = token(admin), conversation = create(adminAuth);
    call(
            "POST",
            "/conversations/" + conversation + "/messages",
            adminAuth,
            Map.of("message", "明确执行三端测试操作", "allowWrites", true),
            UUID.randomUUID().toString())
        .andExpect(status().isCreated());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_coupon WHERE merchant_id=?",
                Integer.class,
                foreignStore))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_post WHERE author_id=?", Integer.class, admin))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_merchant_member WHERE user_id=?",
                Integer.class,
                admin))
        .isZero();
  }

  @Test
  void failedModelRetryAndRepeatedToolCallsNeverDuplicateTheCompletedMutation() throws Exception {
    var index = new java.util.concurrent.atomic.AtomicInteger();
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            invocation -> {
              if (index.getAndIncrement() == 0)
                return api(operation("POST", "/posts"), Map.of(), postInput());
              throw new com.campus.perks.common.exception.ApplicationException(
                  org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                  "AI_PROVIDER_UNAVAILABLE",
                  "测试中断");
            });
    String auth = token(user), conversation = create(auth), key = UUID.randomUUID().toString();
    var input = Map.of("message", "创建一份草稿", "allowWrites", true);
    call("POST", "/conversations/" + conversation + "/messages", auth, input, key)
        .andExpect(status().isServiceUnavailable());
    index.set(0);
    doAnswer(
            invocation ->
                index.getAndIncrement() < 2
                    ? api(operation("POST", "/posts"), Map.of(), postInput())
                    : new AiModelPort.Response("原草稿已确认", List.of()))
        .when(model)
        .chat(anyList(), anyList());
    call("POST", "/conversations/" + conversation + "/messages", auth, input, key)
        .andExpect(status().isOk());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_post WHERE author_id=?", Integer.class, user))
        .isEqualTo(1);
  }

  @Test
  void uncertainMutationBlocksFurtherWritesAndCannotBeBlindlyReplayed() throws Exception {
    var index = new java.util.concurrent.atomic.AtomicInteger();
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            invocation -> {
              if (index.getAndIncrement() == 0)
                return api(operation("POST", "/posts"), Map.of(), postInput());
              db.update(
                  "UPDATE campus_assistant_tool_execution e JOIN campus_assistant_turn t ON"
                      + " t.id=e.turn_id JOIN campus_assistant_conversation c ON"
                      + " c.id=t.conversation_id SET e.status='RUNNING' WHERE c.owner_id=?",
                  user);
              return api(
                  operation("POST", "/posts"),
                  Map.of(),
                  Map.of(
                      "schoolId",
                      "" + school,
                      "content",
                      Map.of(
                          "title",
                          "second forbidden draft",
                          "body",
                          "fixture",
                          "imageIds",
                          List.of())));
            });
    String auth = token(user), conversation = create(auth), key = UUID.randomUUID().toString();
    var input = Map.of("message", "创建草稿", "allowWrites", true);
    call("POST", "/conversations/" + conversation + "/messages", auth, input, key)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSISTANT_ACTION_UNCERTAIN"));
    index.set(0);
    call("POST", "/conversations/" + conversation + "/messages", auth, input, key)
        .andExpect(status().isConflict());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_post WHERE author_id=?", Integer.class, user))
        .isEqualTo(1);
  }

  @Test
  void revocationDuringStreamingStopsFurtherTextAndDoesNotSaveCompletedHistory() throws Exception {
    when(model.stream(anyList(), anyList(), any()))
        .thenAnswer(
            invocation -> {
              java.util.function.Consumer<String> delta = invocation.getArgument(2);
              delta.accept("允许的第一段");
              db.update("UPDATE campus_user SET access_version=access_version+1 WHERE id=?", user);
              delta.accept("撤权后不得输出的私密段落");
              return new AiModelPort.Response("不应保存", List.of());
            });
    String auth = token(user), conversation = create(auth);
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(
                    "http://127.0.0.1:"
                        + port
                        + "/api/v1/assistant/conversations/"
                        + conversation
                        + "/message-streams"))
            .header("Authorization", auth)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"message\":\"撤销流式会话\"}"))
            .build();
    var response =
        java.net.http.HttpClient.newHttpClient()
            .send(
                request,
                java.net.http.HttpResponse.BodyHandlers.ofString(
                    java.nio.charset.StandardCharsets.UTF_8));
    assertThat(response.body())
        .contains("event:error", "INVALID_SESSION")
        .doesNotContain("撤权后不得输出的私密段落", "event:completed");
    verify(store, never()).save(any());
  }

  @Test
  void largeBusinessResultsKeepResourceIdsAndReplayTheSameBoundedResponse() throws Exception {
    var body =
        Map.<String, Object>of(
            "schoolId",
            "" + school,
            "content",
            Map.of("title", "long fixture", "body", "校园".repeat(3000), "imageIds", List.of()));
    var responses = new ArrayList<String>();
    var step = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            invocation -> {
              if (step.getAndIncrement() == 0)
                return api(operation("POST", "/posts"), Map.of(), body);
              List<AiModelPort.Message> messages = invocation.getArgument(0);
              responses.add(messages.get(messages.size() - 1).text());
              throw new com.campus.perks.common.exception.ApplicationException(
                  org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                  "AI_PROVIDER_UNAVAILABLE",
                  "测试中断");
            })
        .when(model)
        .chat(anyList(), anyList());
    String auth = token(user), conversation = create(auth), key = UUID.randomUUID().toString();
    var input = Map.of("message", "生成长草稿", "allowWrites", true);
    call("POST", "/conversations/" + conversation + "/messages", auth, input, key)
        .andExpect(status().isServiceUnavailable());
    step.set(0);
    doAnswer(
            invocation -> {
              if (step.getAndIncrement() == 0)
                return api(operation("POST", "/posts"), Map.of(), body);
              List<AiModelPort.Message> messages = invocation.getArgument(0);
              responses.add(messages.get(messages.size() - 1).text());
              return new AiModelPort.Response("已确认原草稿", List.of());
            })
        .when(model)
        .chat(anyList(), anyList());
    call("POST", "/conversations/" + conversation + "/messages", auth, input, key)
        .andExpect(status().isOk());
    assertThat(responses).hasSize(2);
    assertThat(responses.get(1)).isEqualTo(responses.get(0));
    assertThat(responses.get(0).length()).isLessThanOrEqualTo(6000);
    var result = json.readTree(responses.get(0));
    assertThat(result.path("status").asInt()).isEqualTo(201);
    assertThat(result.path("data").path("id").asText()).isNotBlank();
    assertThat(result.path("truncated").asBoolean()).isTrue();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_post WHERE author_id=?", Integer.class, user))
        .isEqualTo(1);
  }

  @Test
  void exhaustedToolRoundsHaveOneReadOnlySummaryRatherThanLosingConfirmedResults()
      throws Exception {
    doAnswer(
            invocation -> {
              List<AiModelPort.Tool> available = invocation.getArgument(1);
              return available.isEmpty()
                  ? new AiModelPort.Response("已核实完成订单查询", List.of())
                  : new AiModelPort.Response(
                      "",
                      List.of(
                          new AiModelPort.ToolCall(
                              UUID.randomUUID().toString(), "my_orders", "{}")));
            })
        .when(model)
        .chat(anyList(), anyList());
    String auth = token(user), conversation = create(auth);
    send(conversation, auth, "核实当前订单", UUID.randomUUID().toString())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reply").value("已核实完成订单查询"));
    verify(model, times(1)).chat(anyList(), org.mockito.ArgumentMatchers.argThat(List::isEmpty));
  }
}
