package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;

/** Real SQL, real scope/session guards, explicit model fixtures; only this class's IDs cleaned. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.assistant.enabled=true",
      "campus.assistant.marketing.enabled=true",
      "campus.assistant.budget.daily-chat-calls=3",
      "campus.assistant.budget.daily-embedding-calls=3",
      "campus.notifications.dispatch-enabled=false",
      "campus.trade.scheduler-enabled=false",
      "campus.search.rebuild-enabled=false"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
class MarketingStageSixMySqlTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenIssuer issuer;
  @Autowired PublicContentPort content;
  @MockitoBean AiModelPort model;
  @MockitoBean RateLimitPort limit;
  @MockitoBean ConversationStorePort history;
  @MockitoBean VectorSearchPort vectors;
  @MockitoSpyBean AuditPort audit;
  final List<Long> users = new ArrayList<>(),
      stores = new ArrayList<>(),
      coupons = new ArrayList<>();
  long school, owner, manager, cashier, other, user, admin, merchant, otherMerchant, coupon;
  String ownerToken;

  static String key() {
    byte[] b = new byte[32];
    new java.security.SecureRandom().nextBytes(b);
    return Base64.getEncoder().encodeToString(b);
  }

  static final String JWT = key(), OTP = key(), IDENTITY = key();

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) {
    r.add("campus.security.jwt.secret-base64", () -> JWT);
    r.add("campus.auth.code-hash-secret-base64", () -> OTP);
    r.add("campus.auth.identity-key-base64", () -> IDENTITY);
  }

  long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    db.update(
        c -> {
          var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
          return p;
        },
        keys);
    return keys.getKey().longValue();
  }

  long account(String type) {
    long id =
        insert(
            "INSERT INTO campus_user(phone,nickname,account_type) VALUES(?,'marketing-test',?)",
            "mk-" + UUID.randomUUID().toString().substring(0, 20),
            type);
    users.add(id);
    return id;
  }

  long store(long owner) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_merchant(owner_id,school_id,name,category,address,status,description)"
                + " VALUES(?,?,?,'DINING','test-address','APPROVED','适合校园聚餐')",
            owner,
            school,
            "mk-" + UUID.randomUUID());
    stores.add(id);
    member(id, owner, "OWNER");
    return id;
  }

  void member(long store, long account, String role) {
    db.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,?)",
        store,
        account,
        role);
  }

  long coupon(long store) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_coupon(merchant_id,title,coupon_type,rules,total_stock,remaining_stock,valid_from,valid_until,status,review_status,published_at,description)"
                + " VALUES(?,'午间满减','AMOUNT_OFF',?,20,20,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1"
                + " HOUR),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1"
                + " DAY),'PUBLISHED','APPROVED',UTC_TIMESTAMP(3),'午间活动，详见规则')",
            store,
            "{\"threshold\":50,\"amountOff\":8,\"discountRate\":null}");
    coupons.add(id);
    return id;
  }

  @BeforeEach
  void fixtures() {
    school = insert("INSERT INTO campus_school(name) VALUES(?)", "mk-" + UUID.randomUUID());
    owner = account("MERCHANT");
    manager = account("MERCHANT");
    cashier = account("MERCHANT");
    other = account("MERCHANT");
    user = account("USER");
    admin = account("ADMIN");
    merchant = store(owner);
    otherMerchant = store(other);
    member(merchant, manager, "MANAGER");
    member(merchant, cashier, "CASHIER");
    coupon = coupon(merchant);
    ownerToken = token(owner);
    when(model.chat(anyList(), anyList()))
        .thenReturn(
            new AiModelPort.Response(
                "{\"title\":\"同学聚餐推荐\",\"body\":\"欢迎同学来店聚餐，优惠条件以活动页面为准。\"}", List.of()));
    when(limit.acquire(anyString(), anyInt(), any())).thenReturn(true);
    when(vectors.search(anyString(), any(), anyInt())).thenReturn(List.of());
    when(model.embed(anyString())).thenReturn(new float[1024]);
    when(history.history(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
  }

  String token(long account) {
    var now = Instant.now();
    String sid = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version) SELECT"
            + " ?,id,?,?,access_version FROM campus_user WHERE id=?",
        sid,
        Timestamp.from(now),
        Timestamp.from(now.plusSeconds(3600)),
        account);
    return "Bearer " + issuer.issue("" + account, sid, List.of(), now, now.plusSeconds(1800));
  }

  String path(long store) {
    return "/api/v1/merchants/" + store + "/marketing-drafts";
  }

  Map<String, Object> input() {
    return Map.of("brief", "面向同学的午餐文案", "tone", "FRIENDLY", "couponId", "" + coupon);
  }

  ResultActions create(String auth, String key, Object input) throws Exception {
    return mvc.perform(
        post(path(merchant))
            .header("Authorization", auth)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(input)));
  }

  ResultActions action(String method, String path, String auth, Object input) throws Exception {
    var request =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), path)
            .header("Authorization", auth);
    if (input != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input));
    return mvc.perform(request);
  }

  JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
  }

  JsonNode generate() throws Exception {
    return body(
        create(ownerToken, UUID.randomUUID().toString(), input())
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store")));
  }

  JsonNode row(String id) throws Exception {
    return body(
        action("GET", path(merchant) + "/" + id, ownerToken, null).andExpect(status().isOk()));
  }

  @Test
  void createsPrivateDraftFromCanonicalRulesAndReplaysWithoutAnotherModelCall() throws Exception {
    String key = UUID.randomUUID().toString();
    var draft = body(create(ownerToken, key, input()).andExpect(status().isCreated()));
    assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
    assertThat(draft.path("sourceCurrent").asBoolean()).isTrue();
    assertThat(draft.path("sources")).hasSize(2);
    create(ownerToken, key, input())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(draft.path("id").asText()));
    verify(model, times(1)).chat(anyList(), anyList());
    var messages = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(model).chat(messages.capture(), eq(List.of()));
    assertThat(messages.getValue().toString())
        .contains("threshold", "amountOff", "validUntil")
        .doesNotContain("Bearer ", "password_hash", "qualification");
    action("GET", path(merchant) + "/capabilities", ownerToken, null)
        .andExpect(jsonPath("$.dailyChatUsed").value(1));
    var changed = new HashMap<>(input());
    changed.put("brief", "另一个请求");
    create(ownerToken, key, changed).andExpect(status().isConflict());
    assertThat(
            db.queryForObject("SELECT status FROM campus_coupon WHERE id=?", String.class, coupon))
        .isEqualTo("PUBLISHED");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_order WHERE user_id=?", Integer.class, owner))
        .isZero();
  }

  @Test
  void separatesPortalsStoresAndCashierPermissions() throws Exception {
    create(token(user), UUID.randomUUID().toString(), input()).andExpect(status().isForbidden());
    create(token(admin), UUID.randomUUID().toString(), input()).andExpect(status().isForbidden());
    create(token(cashier), UUID.randomUUID().toString(), input()).andExpect(status().isForbidden());
    create(token(other), UUID.randomUUID().toString(), input()).andExpect(status().isNotFound());
    var draft = generate();
    action("GET", path(merchant) + "/" + draft.path("id").asText(), token(cashier), null)
        .andExpect(status().isOk());
    action("GET", path(otherMerchant) + "/" + draft.path("id").asText(), token(other), null)
        .andExpect(status().isNotFound());
    action(
            "PATCH",
            path(merchant) + "/" + draft.path("id").asText(),
            token(cashier),
            Map.of("version", draft.path("version").asInt(), "title", "非法修改", "body", "非法修改"))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCanEditWithVersionAndArchiveButCannotPublish() throws Exception {
    var draft = generate();
    String id = draft.path("id").asText(), auth = token(manager);
    int version = draft.path("version").asInt();
    var edit = Map.of("version", version, "title", "人工核实文案", "body", "条件已核实");
    action("PATCH", path(merchant) + "/" + id, auth, edit)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("人工核实文案"));
    action("PATCH", path(merchant) + "/" + id, auth, edit).andExpect(status().isConflict());
    action(
            "POST",
            path(merchant) + "/" + id + "/status-changes",
            auth,
            Map.of("version", version + 1, "status", "PUBLISHED"))
        .andExpect(status().isBadRequest());
    action(
            "POST",
            path(merchant) + "/" + id + "/status-changes",
            auth,
            Map.of("version", version + 1, "status", "ARCHIVED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
    action(
            "PATCH",
            path(merchant) + "/" + id,
            auth,
            Map.of("version", version + 2, "title", "再次修改", "body", "不可修改"))
        .andExpect(status().isConflict());
    verify(model, times(1)).chat(anyList(), anyList());
  }

  @Test
  void rejectsUnpublishedExpiredAndOtherStoreCouponSourcesBeforeCallingModel() throws Exception {
    long foreign = coupon(otherMerchant);
    var request = new HashMap<>(input());
    request.put("couponId", "" + foreign);
    create(ownerToken, UUID.randomUUID().toString(), request).andExpect(status().isNotFound());
    db.update("UPDATE campus_coupon SET status='OFFLINE' WHERE id=?", coupon);
    create(ownerToken, UUID.randomUUID().toString(), input()).andExpect(status().isNotFound());
    db.update(
        "UPDATE campus_coupon SET status='PUBLISHED',valid_from=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL"
            + " 2 DAY),valid_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 DAY) WHERE id=?",
        coupon);
    create(ownerToken, UUID.randomUUID().toString(), input()).andExpect(status().isNotFound());
    verify(model, never()).chat(anyList(), anyList());
  }

  @Test
  void failedProviderCanRetrySameDraftButNeverExposesRawProviderError() throws Exception {
    when(model.chat(anyList(), anyList()))
        .thenThrow(new RuntimeException("private-provider-message"));
    String key = UUID.randomUUID().toString();
    var response =
        body(create(ownerToken, key, input()).andExpect(status().isServiceUnavailable()));
    assertThat(response.toString()).doesNotContain("private-provider-message");
    String id =
        db.queryForObject(
            "SELECT id FROM campus_marketing_draft WHERE merchant_id=?", String.class, merchant);
    var failed = row(id);
    assertThat(failed.path("status").asText()).isEqualTo("FAILED");
    doReturn(new AiModelPort.Response("{\"title\":\"重试草稿\",\"body\":\"事实待核实\"}", List.of()))
        .when(model)
        .chat(anyList(), anyList());
    action(
            "POST",
            path(merchant) + "/" + id + "/generation-attempts",
            ownerToken,
            Map.of("version", failed.path("version").asInt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attempt").value(2));
    create(ownerToken, key, input())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    verify(model, times(2)).chat(anyList(), anyList());
  }

  @Test
  void exhaustedBudgetBlocksCallsAcrossNewTasksAndFailedAttemptsCount() throws Exception {
    when(model.chat(anyList(), anyList())).thenThrow(new RuntimeException("provider-unavailable"));
    for (int i = 0; i < 3; i++)
      create(ownerToken, UUID.randomUUID().toString(), input())
          .andExpect(status().isServiceUnavailable());
    create(ownerToken, UUID.randomUUID().toString(), input())
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("AI_DAILY_BUDGET_EXCEEDED"));
    verify(model, times(3)).chat(anyList(), anyList());
    assertThat(
            db.queryForObject(
                "SELECT call_count FROM campus_ai_usage WHERE user_id=? AND kind='CHAT'",
                Integer.class,
                owner))
        .isEqualTo(3);
  }

  @Test
  void minuteLimitPreventsProviderBilling() throws Exception {
    when(limit.acquire(anyString(), anyInt(), any())).thenReturn(false);
    create(ownerToken, UUID.randomUUID().toString(), input())
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("MARKETING_RATE_LIMITED"));
    verify(model, never()).chat(anyList(), anyList());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_ai_usage WHERE user_id=?", Integer.class, owner))
        .isZero();
  }

  @Test
  void concurrentSameKeyReturnsAcceptedAndDoesNotHoldSqlLocksDuringModelCall() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var worker = Executors.newSingleThreadExecutor();
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            i -> {
              entered.countDown();
              if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("test-timeout");
              return new AiModelPort.Response("{\"title\":\"并发文案\",\"body\":\"内容待核实\"}", List.of());
            });
    String key = UUID.randomUUID().toString();
    try {
      var first = worker.submit(() -> create(ownerToken, key, input()).andReturn());
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      create(ownerToken, key, input())
          .andExpect(status().isAccepted())
          .andExpect(header().exists("Location"));
      action("GET", path(merchant), token(cashier), null).andExpect(status().isOk());
      release.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
      verify(model, times(1)).chat(anyList(), anyList());
    } finally {
      release.countDown();
      worker.shutdownNow();
      worker.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void changedSourceAndRevokedSessionCannotCommitAnAnswer() throws Exception {
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            i -> {
              db.update("UPDATE campus_coupon SET status='OFFLINE' WHERE id=?", coupon);
              return new AiModelPort.Response("{\"title\":\"旧优惠\",\"body\":\"旧事实\"}", List.of());
            });
    create(ownerToken, UUID.randomUUID().toString(), input())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MARKETING_SOURCE_CHANGED"));
    db.update("UPDATE campus_coupon SET status='PUBLISHED' WHERE id=?", coupon);
    doAnswer(
            i -> {
              db.update(
                  "UPDATE campus_auth_session SET revoked_at=UTC_TIMESTAMP(3) WHERE user_id=?",
                  owner);
              return new AiModelPort.Response("{\"title\":\"旧权限\",\"body\":\"不能提交\"}", List.of());
            })
        .when(model)
        .chat(anyList(), anyList());
    create(ownerToken, UUID.randomUUID().toString(), input()).andExpect(status().isUnauthorized());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_marketing_draft WHERE merchant_id=? AND body IS NOT"
                    + " NULL",
                Integer.class,
                merchant))
        .isZero();
  }

  @Test
  void staleWorkerCannotCompleteOrFailANewerLease() throws Exception {
    String replacement = UUID.randomUUID().toString();
    when(model.chat(anyList(), anyList()))
        .thenAnswer(
            i -> {
              db.update(
                  "UPDATE campus_marketing_draft SET"
                      + " lease_token=?,attempt=attempt+1,locked_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
                      + " 2 MINUTE) WHERE merchant_id=?",
                  replacement,
                  merchant);
              return new AiModelPort.Response("{\"title\":\"旧进程\",\"body\":\"不可覆盖\"}", List.of());
            });
    create(ownerToken, UUID.randomUUID().toString(), input()).andExpect(status().isConflict());
    assertThat(
            db.queryForObject(
                "SELECT lease_token FROM campus_marketing_draft WHERE merchant_id=?",
                String.class,
                merchant))
        .isEqualTo(replacement);
    assertThat(
            db.queryForObject(
                "SELECT status FROM campus_marketing_draft WHERE merchant_id=?",
                String.class,
                merchant))
        .isEqualTo("GENERATING");
  }

  @Test
  void expiredLeaseRetriesWithSameIdAndArchivedDraftsNeverRegenerate() throws Exception {
    when(model.chat(anyList(), anyList())).thenThrow(new RuntimeException("provider-unavailable"));
    String key = UUID.randomUUID().toString();
    create(ownerToken, key, input()).andExpect(status().isServiceUnavailable());
    String id =
        db.queryForObject(
            "SELECT id FROM campus_marketing_draft WHERE merchant_id=?", String.class, merchant);
    db.update(
        "UPDATE campus_marketing_draft SET"
            + " status='GENERATING',lease_token=?,locked_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1"
            + " SECOND) WHERE id=?",
        UUID.randomUUID().toString(),
        id);
    var expired = row(id);
    assertThat(expired.path("errorCode").asText()).isEqualTo("MARKETING_LEASE_EXPIRED");
    doReturn(new AiModelPort.Response("{\"title\":\"重试成功\",\"body\":\"生成草稿\"}", List.of()))
        .when(model)
        .chat(anyList(), anyList());
    var generated =
        body(
            action(
                    "POST",
                    path(merchant) + "/" + id + "/generation-attempts",
                    ownerToken,
                    Map.of("version", expired.path("version").asInt()))
                .andExpect(status().isOk()));
    action(
            "POST",
            path(merchant) + "/" + id + "/status-changes",
            ownerToken,
            Map.of("version", generated.path("version").asInt(), "status", "ARCHIVED"))
        .andExpect(status().isOk());
    create(ownerToken, key, input())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
    verify(model, times(2)).chat(anyList(), anyList());
  }

  @Test
  void malformedModelOutputAndToolCallsCannotWriteDraftContent() throws Exception {
    when(model.chat(anyList(), anyList())).thenReturn(new AiModelPort.Response("{}", List.of()));
    create(ownerToken, UUID.randomUUID().toString(), input()).andExpect(status().isBadGateway());
    when(model.chat(anyList(), anyList()))
        .thenReturn(
            new AiModelPort.Response(
                "", List.of(new AiModelPort.ToolCall("bad", "publish_coupon", "{}"))));
    create(ownerToken, UUID.randomUUID().toString(), input()).andExpect(status().isBadGateway());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_marketing_draft WHERE merchant_id=? AND"
                    + " status='DRAFT'",
                Integer.class,
                merchant))
        .isZero();
  }

  @Test
  void snapshotsRemainPrivateWhenSourcesChangeAndSuspensionPreventsWrites() throws Exception {
    var draft = generate();
    String id = draft.path("id").asText();
    db.update("UPDATE campus_coupon SET status='OFFLINE' WHERE id=?", coupon);
    assertThat(row(id).path("sourceCurrent").asBoolean()).isFalse();
    db.update("UPDATE campus_merchant SET status='SUSPENDED' WHERE id=?", merchant);
    action("GET", path(merchant) + "/" + id, ownerToken, null).andExpect(status().isOk());
    action(
            "PATCH",
            path(merchant) + "/" + id,
            ownerToken,
            Map.of("version", draft.path("version").asInt(), "title", "已暂停", "body", "不应写入"))
        .andExpect(status().isConflict());
  }

  @Test
  void failedAuditRollsBackDraftCompletionAndMetadataIncludesVersionAndExpiry() throws Exception {
    AuditPort target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
    doThrow(new IllegalStateException("audit-failure"))
        .when(target)
        .record(anyString(), eq("MARKETING_DRAFT_GENERATED"), anyString(), anyString(), anyMap());
    create(ownerToken, UUID.randomUUID().toString(), input())
        .andExpect(status().isServiceUnavailable());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_marketing_draft WHERE merchant_id=? AND body IS NOT"
                    + " NULL",
                Integer.class,
                merchant))
        .isZero();
    var source = content.find(List.of(new PublicContentPort.Key("COUPON", "" + coupon))).get(0);
    assertThat(source.revision().version()).isNotBlank();
    assertThat(source.revision().validUntil()).isAfter(Instant.now());
  }

  @Test
  void assistantAndMarketingShareBudgetAndUtcDaysHaveIndependentCounters() throws Exception {
    var saved = new java.util.concurrent.ConcurrentHashMap<String, ConversationStorePort.Turn>();
    doAnswer(
            i -> {
              ConversationStorePort.Turn turn = i.getArgument(0);
              saved.put(turn.id(), turn);
              return null;
            })
        .when(history)
        .save(any());
    when(history.completed(anyString(), anyString(), anyString()))
        .thenAnswer(i -> saved.get(i.getArgument(0)));
    String conversation =
        body(action("POST", "/api/v1/assistant/conversations", ownerToken, Map.of())
                .andExpect(status().isCreated()))
            .path("id")
            .asText();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/v1/assistant/conversations/" + conversation + "/messages")
                  .header("Authorization", ownerToken)
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsBytes(Map.of("message", "你好" + i))))
          .andExpect(status().isCreated());
    generate();
    create(ownerToken, UUID.randomUUID().toString(), input())
        .andExpect(status().isTooManyRequests());
    verify(model, times(3)).chat(anyList(), anyList());
    db.update(
        "UPDATE campus_ai_usage SET usage_day=DATE_SUB(UTC_DATE(),INTERVAL 1 DAY) WHERE user_id=?",
        owner);
    generate();
    assertThat(
            db.queryForObject(
                "SELECT call_count FROM campus_ai_usage WHERE user_id=? AND usage_day=UTC_DATE()"
                    + " AND kind='CHAT'",
                Integer.class,
                owner))
        .isEqualTo(1);
  }

  @Test
  void attemptsAreBoundedAndOnlyCreatorMayRetry() throws Exception {
    when(model.chat(anyList(), anyList())).thenThrow(new RuntimeException("provider-unavailable"));
    String key = UUID.randomUUID().toString();
    for (int i = 0; i < 3; i++)
      create(ownerToken, key, input()).andExpect(status().isServiceUnavailable());
    create(ownerToken, key, input())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MARKETING_ATTEMPTS_EXHAUSTED"));
    String id =
        db.queryForObject(
            "SELECT id FROM campus_marketing_draft WHERE merchant_id=?", String.class, merchant);
    var draft = row(id);
    action(
            "POST",
            path(merchant) + "/" + id + "/generation-attempts",
            token(manager),
            Map.of("version", draft.path("version").asInt()))
        .andExpect(status().isForbidden());
    verify(model, times(3)).chat(anyList(), anyList());
  }

  @AfterEach
  void cleanup() {
    for (long store : stores)
      db.update("DELETE FROM campus_marketing_draft WHERE merchant_id=?", store);
    for (long account : users) {
      db.update("DELETE FROM campus_ai_usage WHERE user_id=?", account);
      db.update("DELETE FROM campus_audit_event WHERE actor_id=?", account);
      db.update(
          "DELETE FROM campus_assistant_turn WHERE conversation_id IN (SELECT id FROM"
              + " campus_assistant_conversation WHERE owner_id=?)",
          account);
      db.update("DELETE FROM campus_assistant_conversation WHERE owner_id=?", account);
      db.update("DELETE FROM campus_auth_session WHERE user_id=?", account);
    }
    for (long coupon : coupons) db.update("DELETE FROM campus_coupon WHERE id=?", coupon);
    for (long store : stores) {
      db.update("DELETE FROM campus_merchant_member WHERE merchant_id=?", store);
      db.update("DELETE FROM campus_merchant WHERE id=?", store);
    }
    for (long account : users) db.update("DELETE FROM campus_user WHERE id=?", account);
    if (school != 0) db.update("DELETE FROM campus_school WHERE id=?", school);
  }
}
