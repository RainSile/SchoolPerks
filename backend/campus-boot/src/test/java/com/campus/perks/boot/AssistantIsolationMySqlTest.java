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
    for (long id : users) {
      db.update("DELETE FROM campus_ai_usage WHERE user_id=?", id);
      db.update("DELETE FROM campus_audit_event WHERE actor_id=?", id);
      db.update(
          "DELETE FROM campus_assistant_turn WHERE conversation_id IN (SELECT id FROM"
              + " campus_assistant_conversation WHERE owner_id=?)",
          id);
      db.update("DELETE FROM campus_assistant_conversation WHERE owner_id=?", id);
      db.update("DELETE FROM campus_auth_session WHERE user_id=?", id);
      db.update("DELETE FROM campus_user_role WHERE user_id=?", id);
    }
    for (long post : posts) db.update("DELETE FROM campus_post WHERE id=?", post);
    for (long role : roles) {
      db.update("DELETE FROM campus_role_permission WHERE role_id=?", role);
      db.update("DELETE FROM campus_role WHERE id=?", role);
    }
    for (long id : users) db.update("DELETE FROM campus_user WHERE id=?", id);
    if (school != 0) db.update("DELETE FROM campus_school WHERE id=?", school);
    saved.clear();
  }
}
