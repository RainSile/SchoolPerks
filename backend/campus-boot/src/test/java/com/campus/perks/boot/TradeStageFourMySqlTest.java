package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.*;
import com.campus.perks.notification.serviceImpl.NotificationInbox;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.campus.perks.trade.mapper.TradeMapper;
import com.campus.perks.trade.serviceImpl.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;

/** Finite MySQL transaction/concurrency checks. Provider mocks exist only in test sources. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.notifications.dispatch-enabled=false",
      "campus.trade.scheduler-enabled=false",
      "campus.trade.refund-max-attempts=2",
      "campus.trade.retry-base=1s",
      "campus.trade.retry-max=2s"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
@Transactional
@Import(TradeStageFourMySqlTest.TimeConfig.class)
class TradeStageFourMySqlTest {
  private static final AtomicLong PHONES = new AtomicLong(51000000);

  private static String key() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getEncoder().encodeToString(b);
  }

  private static final String JWT = key(), OTP = key(), IDENTITY = key();

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) {
    r.add("campus.security.jwt.secret-base64", () -> JWT);
    r.add("campus.auth.code-hash-secret-base64", () -> OTP);
    r.add("campus.auth.identity-key-base64", () -> IDENTITY);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenIssuer issuer;
  @Autowired OrderServiceImpl service;
  @Autowired TradeScheduler scheduler;
  @Autowired TradeMapper mapper;
  @Autowired NotificationInbox inbox;
  @Autowired MutableClock clock;
  @MockitoBean SandboxPaymentPort provider;
  @MockitoSpyBean AuditPort audit;
  @MockitoSpyBean OutboxPort outbox;
  private long school, owner, buyer, buyer2, cashier, store;
  private final List<Long> users = new ArrayList<>();

  private long insert(String sql, Object... args) {
    var k = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
          return p;
        },
        k);
    return k.getKey().longValue();
  }

  private long user() {
    String phone;
    do {
      phone = "139" + PHONES.incrementAndGet();
    } while (jdbc.queryForObject(
            "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, phone)
        > 0);
    long id = insert("INSERT INTO campus_user(phone,nickname) VALUES(?,'stage4-test')", phone);
    users.add(id);
    return id;
  }

  @BeforeEach
  void fixtures() {
    clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    when(provider.available()).thenReturn(true);
    when(provider.checkout(anyString(), any(), anyString(), any()))
        .thenReturn("https://openapi.alipaydev.com/gateway.do?test=1");
    when(provider.query(anyString()))
        .thenAnswer(
            i ->
                new SandboxPaymentPort.PaymentState(
                    i.getArgument(0), null, null, "WAIT_BUYER_PAY"));
    school =
        insert("INSERT INTO campus_school(name) VALUES(?)", "stage4-test-" + UUID.randomUUID());
    owner = user();
    buyer = user();
    buyer2 = user();
    cashier = user();
    jdbc.update("UPDATE campus_user SET account_type='MERCHANT' WHERE id IN (?,?)", owner, cashier);
    String file = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at,completed_at)"
            + " VALUES(?,?,'MERCHANT_QUALIFICATION','test.pdf','application/pdf',64,?,?,'READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
            + " 1 HOUR),UTC_TIMESTAMP(3))",
        file,
        owner,
        "stage4/pending/" + file,
        "stage4/sealed/" + file);
    store =
        insert(
            "INSERT INTO"
                + " campus_merchant(owner_id,school_id,name,category,address,status,qualification_file_id)"
                + " VALUES(?,?,'stage4-test','DINING','stage4-test','APPROVED',?)",
            owner,
            school,
            file);
    jdbc.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role)"
            + " VALUES(?,?,'OWNER'),(?,?,'CASHIER')",
        store,
        owner,
        store,
        cashier);
  }

  private String token(long user) {
    String sid = UUID.randomUUID().toString();
    var now = clock.instant();
    jdbc.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version)"
            + " VALUES(?,?,?,?,(SELECT access_version FROM campus_user WHERE id=?))",
        sid,
        user,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now.plus(Duration.ofDays(3))),
        user);
    return "Bearer " + issuer.issue("" + user, sid, List.of(), now, now.plus(Duration.ofHours(1)));
  }

  private ResultActions post(String path, long user, Object body) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1" + path)
            .header("Authorization", token(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private ResultActions get(String path, long user) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1" + path)
            .header("Authorization", token(user)));
  }

  private JsonNode body(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private String offer(String kind, int stock) throws Exception {
    String id =
        body(post(
                    "/merchants/" + store + "/trade-offers",
                    owner,
                    Map.of(
                        "title",
                        "stage4 lunch",
                        "description",
                        "one meal",
                        "kind",
                        kind,
                        "price",
                        "19.90",
                        "stock",
                        stock,
                        "startsAt",
                        clock.instant().minusSeconds(60),
                        "endsAt",
                        clock.instant().plusSeconds(7200),
                        "groupSize",
                        kind.equals("GROUP") ? 2 : 1,
                        "groupDurationSeconds",
                        kind.equals("GROUP") ? 600 : 0))
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    mvc.perform(
            patch("/api/v1/trade-offers/" + id)
                .header("Authorization", token(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\",\"version\":0}"))
        .andExpect(status().isOk());
    return id;
  }

  private ResultActions orderRequest(String offer, String group, long user, String key)
      throws Exception {
    var input = new HashMap<String, String>();
    input.put("offerId", offer);
    if (group != null) input.put("groupId", group);
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/orders")
            .header("Authorization", token(user))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(input)));
  }

  private JsonNode order(String offer, String group, long user) throws Exception {
    return body(
        orderRequest(offer, group, user, UUID.randomUUID().toString())
            .andExpect(status().isCreated()));
  }

  private JsonNode order(String offer) throws Exception {
    return order(offer, null, buyer);
  }

  private String pay(JsonNode o, long user) throws Exception {
    return body(post("/orders/" + o.get("id").asText() + "/payments", user, Map.of())
            .andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  private SandboxPaymentPort.VerifiedPayment notice(JsonNode o, String notification) {
    return new SandboxPaymentPort.VerifiedPayment(
        notification,
        o.get("orderNo").asText(),
        "T" + o.get("id").asText(),
        new BigDecimal("19.90"),
        "TRADE_SUCCESS");
  }

  private void paid(JsonNode o, long user) throws Exception {
    pay(o, user);
    service.accept(notice(o, UUID.randomUUID().toString()), "testhash" + o.get("id").asText());
  }

  private String state(JsonNode o) {
    return jdbc.queryForObject(
        "SELECT status FROM campus_order WHERE id=?", String.class, o.get("id").asText());
  }

  private int stock(String offer) {
    return jdbc.queryForObject(
        "SELECT stock_available FROM campus_trade_offer WHERE id=?", Integer.class, offer);
  }

  @Test
  void serverSnapshotAndOrderIdempotencyAreEnforced() throws Exception {
    String offer = offer("REGULAR", 3), key = UUID.randomUUID().toString();
    var o = body(orderRequest(offer, null, buyer, key).andExpect(status().isCreated()));
    orderRequest(offer, null, buyer, key)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(o.get("id").asText()));
    assertThat(o.get("amount").asText()).isEqualTo("19.90");
    assertThat(o.at("/offerSnapshot/price").asText()).isEqualTo("19.90");
    assertThat(stock(offer)).isEqualTo(2);
    orderRequest(offer, "123", buyer, key).andExpect(status().isConflict());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/orders")
                .header("Authorization", token(buyer))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"offerId\":\"" + offer + "\",\"amount\":0.01}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cashierCannotPublishAndUnrelatedUserCannotReadOrders() throws Exception {
    String offer = offer("REGULAR", 2);
    var o = order(offer);
    String path = "/orders/" + o.get("id").asText();
    get(path, buyer2).andExpect(status().isNotFound());
    post(path + "/cancellations", buyer2, Map.of()).andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/trade-offers/" + offer)
                .header("Authorization", token(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"OFFLINE\",\"version\":1}"))
        .andExpect(status().isForbidden());
    get("/merchants/" + store + "/orders", buyer2).andExpect(status().isForbidden());
  }

  @Test
  void seckillHasOneActiveOrderPerUserAndNoOversell() throws Exception {
    String offer = offer("SECKILL", 1);
    order(offer);
    orderRequest(offer, null, buyer, UUID.randomUUID().toString())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SECKILL_LIMIT"));
    orderRequest(offer, null, buyer2, UUID.randomUUID().toString())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
  }

  @Test
  void cancellationRestoresExactlyOneReservation() throws Exception {
    String offer = offer("REGULAR", 2);
    var o = order(offer);
    String path = "/orders/" + o.get("id").asText() + "/cancellations";
    post(path, buyer, Map.of()).andExpect(status().isCreated());
    post(path, buyer, Map.of()).andExpect(status().isOk());
    service.sweepOrder(o.get("id").asLong());
    assertThat(stock(offer)).isEqualTo(2);
  }

  @Test
  void missingSandboxNeverCreatesFakePayment() throws Exception {
    var o = order(offer("REGULAR", 2));
    when(provider.available()).thenReturn(false);
    post("/orders/" + o.get("id").asText() + "/payments", buyer, Map.of())
        .andExpect(status().isServiceUnavailable());
    assertThat(mapper.payment(o.get("id").asLong())).isNull();
    assertThat(state(o)).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void duplicatePaymentAndCallbackHaveOneBusinessTransition() throws Exception {
    var o = order(offer("REGULAR", 2));
    String payment = pay(o, buyer);
    post("/orders/" + o.get("id").asText() + "/payments", buyer, Map.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment));
    var notice = notice(o, "same-notification");
    service.accept(notice, "digest1");
    service.accept(notice, "digest1");
    service.accept(notice(o, "another-notification"), "digest2");
    assertThat(state(o)).isEqualTo("PAID");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_audit_event WHERE action='ORDER_PAID' AND"
                    + " resource_id=?",
                Integer.class,
                o.get("id").asText()))
        .isEqualTo(1);
    assertThatThrownBy(() -> service.accept(notice, "different")).hasMessageContaining("通知内容");
  }

  @Test
  void callbackAmountMismatchRollsBackPaymentAndReceipt() throws Exception {
    var o = order(offer("REGULAR", 2));
    pay(o, buyer);
    var bad =
        new SandboxPaymentPort.VerifiedPayment(
            "bad", o.get("orderNo").asText(), "T123", new BigDecimal("0.01"), "TRADE_SUCCESS");
    assertThatThrownBy(() -> service.accept(bad, "bad")).hasMessageContaining("不匹配");
    assertThat(state(o)).isEqualTo("PENDING_PAYMENT");
    assertThat(mapper.callbackHash("bad")).isNull();
  }

  @Test
  void timeoutCancelsAndRejectsPayment() throws Exception {
    String offer = offer("REGULAR", 2);
    var o = order(offer);
    clock.now = clock.now.plusSeconds(901);
    service.sweepOrder(o.get("id").asLong());
    assertThat(state(o)).isEqualTo("CANCELLED");
    assertThat(stock(offer)).isEqualTo(2);
    post("/orders/" + o.get("id").asText() + "/payments", buyer, Map.of())
        .andExpect(status().isConflict());
  }

  @Test
  void groupRequiresPaidMembersAndSettlesAllMembers() throws Exception {
    String offer = offer("GROUP", 2);
    var first = order(offer);
    String group = first.get("groupId").asText();
    var second = order(offer, group, buyer2);
    paid(first, buyer);
    assertThat(state(first)).isEqualTo("GROUP_PENDING");
    post("/orders/" + first.get("id").asText() + "/redemption-codes", buyer, Map.of())
        .andExpect(status().isConflict());
    paid(second, buyer2);
    service.sweepOrder(first.get("id").asLong());
    assertThat(state(first)).isEqualTo("PAID");
    assertThat(state(second)).isEqualTo("PAID");
    assertThat(mapper.group(Long.parseLong(group), true).paidCount()).isEqualTo(2);
    service.accept(notice(first, "duplicate-first"), "again");
    assertThat(mapper.group(Long.parseLong(group), true).paidCount()).isEqualTo(2);
  }

  @Test
  void groupFailureRefundsPaidAndCancelsUnpaidOnlyOnce() throws Exception {
    String offer = offer("GROUP", 2);
    var first = order(offer);
    var second = order(offer, first.get("groupId").asText(), buyer2);
    paid(first, buyer);
    clock.now = clock.now.plusSeconds(601);
    service.sweepOrder(first.get("id").asLong());
    service.sweepOrder(second.get("id").asLong());
    assertThat(state(first)).isEqualTo("REFUND_PENDING");
    assertThat(state(second)).isEqualTo("CANCELLED");
    assertThat(stock(offer)).isEqualTo(1);
    scheduler.runOnce();
    scheduler.runOnce();
    assertThat(state(first)).isEqualTo("REFUNDED");
    assertThat(stock(offer)).isEqualTo(2);
    verify(provider, times(1))
        .refund(eq(first.get("orderNo").asText()), anyString(), eq(new BigDecimal("19.90")));
  }

  @Test
  void latePaymentAfterCancellationAlwaysRefunds() throws Exception {
    String offer = offer("REGULAR", 2);
    var o = order(offer);
    pay(o, buyer);
    post("/orders/" + o.get("id").asText() + "/cancellations", buyer, Map.of())
        .andExpect(status().isCreated());
    service.accept(notice(o, "late"), "late");
    assertThat(state(o)).isEqualTo("REFUND_PENDING");
    assertThat(stock(offer)).isEqualTo(2);
    scheduler.runOnce();
    assertThat(state(o)).isEqualTo("REFUNDED");
    assertThat(stock(offer)).isEqualTo(2);
  }

  @Test
  void refundRetryPreservesProviderIdempotencyAndOneStockRelease() throws Exception {
    String offer = offer("REGULAR", 2);
    var o = order(offer);
    paid(o, buyer);
    String path = "/orders/" + o.get("id").asText() + "/refunds";
    String refund =
        body(post(path, buyer, Map.of()).andExpect(status().isCreated())).get("id").asText();
    post(path, buyer, Map.of()).andExpect(status().isOk());
    doThrow(new IllegalStateException("provider down"))
        .when(provider)
        .refund(anyString(), anyString(), any());
    scheduler.runOnce();
    assertThat(state(o)).isEqualTo("REFUND_PENDING");
    clock.now = clock.now.plusSeconds(3);
    scheduler.runOnce();
    assertThat(mapper.refund(o.get("id").asLong()).status()).isEqualTo("FAILED");
    post(path + "/" + refund + "/retries", buyer, Map.of()).andExpect(status().isAccepted());
    doNothing().when(provider).refund(anyString(), anyString(), any());
    scheduler.runOnce();
    assertThat(state(o)).isEqualTo("REFUNDED");
    assertThat(stock(offer)).isEqualTo(2);
    verify(provider, times(3))
        .refund(eq(o.get("orderNo").asText()), eq(refund), eq(new BigDecimal("19.90")));
  }

  @Test
  void redemptionIsScopedExpiringAndIdempotent() throws Exception {
    var o = order(offer("REGULAR", 2));
    paid(o, buyer);
    String code =
        body(post("/orders/" + o.get("id").asText() + "/redemption-codes", buyer, Map.of())
                .andExpect(status().isOk()))
            .get("code")
            .asText();
    var input = Map.of("orderId", o.get("id").asText(), "code", code);
    post("/merchants/" + store + "/order-redemptions", buyer2, input)
        .andExpect(status().isForbidden());
    post("/merchants/" + store + "/order-redemptions", cashier, input)
        .andExpect(status().isCreated());
    post("/merchants/" + store + "/order-redemptions", cashier, input).andExpect(status().isOk());
    post("/orders/" + o.get("id").asText() + "/refunds", buyer, Map.of())
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT redemption_hash FROM campus_order WHERE id=?",
                String.class,
                o.get("id").asText()))
        .isNotEqualTo(code);
  }

  @Test
  void orderEventsHaveExplicitIdempotentInboxRouting() throws Exception {
    var o = order(offer("REGULAR", 1));
    String eid = UUID.randomUUID().toString();
    var event =
        new EventPublisherPort.Event(
            eid,
            "ORDER_PAID",
            o.get("id").asText(),
            clock.instant(),
            Map.of(
                "userId", "" + buyer, "orderId", o.get("id").asText(), "merchantId", "" + store));
    var first = inbox.deliver(event);
    assertThat(inbox.deliver(event).id()).isEqualTo(first.id());
    assertThat(first.payload().has("userId")).isFalse();
  }

  /**
   * Committed fixtures are required for real independent transactions. Cleanup is by exact fixture
   * IDs.
   */
  @Test
  void finiteConcurrencyCannotOversellAndPaymentCancelRaceCannotFulfillTwice() throws Exception {
    String offer = offer("SECKILL", 1);
    String b1 = token(buyer), b2 = token(buyer2);
    TestTransaction.flagForCommit();
    TestTransaction.end();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var start = new CountDownLatch(1);
      var futures = new ArrayList<Future<MvcResult>>();
      for (String bearer : List.of(b1, b2))
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                  "/api/v1/orders")
                              .header("Authorization", bearer)
                              .header("Idempotency-Key", UUID.randomUUID().toString())
                              .contentType(MediaType.APPLICATION_JSON)
                              .content("{\"offerId\":\"" + offer + "\"}"))
                      .andReturn();
                }));
      start.countDown();
      var results = new ArrayList<MvcResult>();
      for (var future : futures) results.add(future.get(15, TimeUnit.SECONDS));
      assertThat(results.stream().map(r -> r.getResponse().getStatus()).sorted().toList())
          .containsExactly(201, 409);
      assertThat(stock(offer)).isZero();
      var winner =
          results.stream()
              .filter(r -> r.getResponse().getStatus() == 201)
              .findFirst()
              .orElseThrow();
      var o = json.readTree(winner.getResponse().getContentAsString());
      long purchaser =
          jdbc.queryForObject(
              "SELECT user_id FROM campus_order WHERE id=?", Long.class, o.get("id").asText());
      pay(o, purchaser);
      String bearer = token(purchaser);
      var race = new CountDownLatch(1);
      var cancel =
          pool.submit(
              () -> {
                race.await();
                return mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/orders/" + o.get("id").asText() + "/cancellations")
                            .header("Authorization", bearer))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var callback =
          pool.submit(
              () -> {
                race.await();
                service.accept(notice(o, "racing"), "racing");
                return true;
              });
      race.countDown();
      int cancelled = cancel.get(15, TimeUnit.SECONDS);
      assertThat(callback.get(15, TimeUnit.SECONDS)).isTrue();
      assertThat(state(o)).isIn("PAID", "REFUND_PENDING");
      assertThat(stock(offer)).isEqualTo(cancelled == 201 ? 1 : 0);
      assertThat(cancelled).isIn(201, 409);
    } finally {
      pool.shutdownNow();
      cleanupCommitted();
      TestTransaction.start();
    }
  }

  @Test
  void failedOutboxRollsBackCancellationAndFailedLocalRefundCommitCanRecover() throws Exception {
    String offer = offer("REGULAR", 2);
    var o = order(offer);
    pay(o, buyer);
    TestTransaction.flagForCommit();
    TestTransaction.end();
    OutboxPort outboxTarget =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
    AuditPort auditTarget =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
    try {
      doThrow(new IllegalStateException("test outbox failure"))
          .when(outboxTarget)
          .append(argThat(e -> e != null && e.type().equals("ORDER_CANCELLED")));
      assertThatThrownBy(
              () -> post("/orders/" + o.get("id").asText() + "/cancellations", buyer, Map.of()))
          .hasRootCauseMessage("test outbox failure");
      assertThat(state(o)).isEqualTo("PENDING_PAYMENT");
      assertThat(stock(offer)).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM campus_trade_cancellation WHERE order_id=?",
                  Integer.class,
                  o.get("id").asText()))
          .isZero();
      reset(outboxTarget);
      service.accept(notice(o, "paid-before-refund"), "paid-before-refund");
      var r =
          body(
              post("/orders/" + o.get("id").asText() + "/refunds", buyer, Map.of())
                  .andExpect(status().isCreated()));
      doThrow(new IllegalStateException("test audit failure"))
          .when(auditTarget)
          .record(anyString(), eq("ORDER_REFUNDED"), anyString(), anyString(), anyMap());
      scheduler.runOnce();
      assertThat(state(o)).isEqualTo("REFUND_PENDING");
      assertThat(stock(offer)).isEqualTo(1);
      reset(auditTarget);
      clock.now = clock.now.plusSeconds(3);
      scheduler.runOnce();
      assertThat(state(o)).isEqualTo("REFUNDED");
      assertThat(stock(offer)).isEqualTo(2);
      verify(provider, times(2))
          .refund(
              eq(o.get("orderNo").asText()), eq(r.get("id").asText()), eq(new BigDecimal("19.90")));
    } finally {
      reset(outboxTarget, auditTarget);
      cleanupCommitted();
      TestTransaction.start();
    }
  }

  @Test
  void codeExpiresAndRefundLeaseCanBeRecoveredWithoutChangingId() throws Exception {
    var o = order(offer("REGULAR", 2));
    paid(o, buyer);
    String value =
        body(post("/orders/" + o.get("id").asText() + "/redemption-codes", buyer, Map.of())
                .andExpect(status().isOk()))
            .get("code")
            .asText();
    clock.now = clock.now.plusSeconds(121);
    post(
            "/merchants/" + store + "/order-redemptions",
            cashier,
            Map.of("orderId", o.get("id").asText(), "code", value))
        .andExpect(status().isConflict());
    post("/orders/" + o.get("id").asText() + "/refunds", buyer, Map.of())
        .andExpect(status().isCreated());
    var first = service.leaseRefund();
    assertThat(first).isNotNull();
    assertThat(service.leaseRefund()).isNull();
    clock.now = clock.now.plusSeconds(121);
    var recovered = service.leaseRefund();
    assertThat(recovered.id()).isEqualTo(first.id());
    assertThat(recovered.lease()).isNotEqualTo(first.lease());
    service.finishRefund(first);
    assertThat(state(o)).isEqualTo("REFUND_PENDING");
    service.finishRefund(recovered);
    assertThat(state(o)).isEqualTo("REFUNDED");
  }

  @Test
  void callbackEndpointRejectsDuplicateFieldsAndAcceptsVerifiedReceiptIdempotently()
      throws Exception {
    var o = order(offer("REGULAR", 2));
    pay(o, buyer);
    when(provider.verify(anyMap())).thenReturn(notice(o, "form-notice"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/payment-notifications/alipay-sandbox")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("notify_id", "one", "two"))
        .andExpect(status().isBadRequest());
    for (int i = 0; i < 2; i++)
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/v1/payment-notifications/alipay-sandbox")
                  .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                  .param("notify_id", "form-notice"))
          .andExpect(status().isOk())
          .andExpect(content().string("success"));
    assertThat(state(o)).isEqualTo("PAID");
  }

  @Test
  void impossibleGroupStockAndUnboundedFutureSaleAreRejected() throws Exception {
    var input =
        new HashMap<String, Object>(
            Map.of(
                "title",
                "test",
                "description",
                "test",
                "kind",
                "GROUP",
                "price",
                "19.90",
                "stock",
                1,
                "startsAt",
                clock.instant(),
                "endsAt",
                clock.instant().plusSeconds(3600),
                "groupSize",
                2,
                "groupDurationSeconds",
                600));
    post("/merchants/" + store + "/trade-offers", owner, input)
        .andExpect(status().isUnprocessableEntity());
    input.put("stock", 2);
    input.put("startsAt", clock.instant().plus(Duration.ofDays(365)));
    input.put("endsAt", clock.instant().plus(Duration.ofDays(366)));
    post("/merchants/" + store + "/trade-offers", owner, input)
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void emptyExpiredGroupStillClosesWithoutLiveOrders() throws Exception {
    var o = order(offer("GROUP", 2));
    post("/orders/" + o.get("id").asText() + "/cancellations", buyer, Map.of())
        .andExpect(status().isCreated());
    clock.now = clock.now.plusSeconds(601);
    scheduler.runOnce();
    assertThat(mapper.group(o.get("groupId").asLong(), true).status()).isEqualTo("FAILED");
  }

  @Test
  void absentProviderOrderRemainsReconciliableForLatePayment() throws Exception {
    String offer = offer("REGULAR", 2);
    var o = order(offer);
    String p = pay(o, buyer);
    post("/orders/" + o.get("id").asText() + "/cancellations", buyer, Map.of())
        .andExpect(status().isCreated());
    when(provider.query(anyString()))
        .thenAnswer(
            i -> new SandboxPaymentPort.PaymentState(i.getArgument(0), null, null, "NOT_FOUND"));
    scheduler.runOnce();
    clock.now = clock.now.plusSeconds(31);
    scheduler.runOnce();
    assertThat(mapper.payment(o.get("id").asLong()).status()).isEqualTo("CREATED");
    verify(provider, times(4)).query(o.get("orderNo").asText());
    service.accept(notice(o, "eventually-paid"), "eventually-paid");
    assertThat(state(o)).isEqualTo("REFUND_PENDING");
    assertThat(stock(offer)).isEqualTo(2);
  }

  private void cleanupCommitted() {
    SearchFixtureCleanup.resources(
        jdbc,
        "OFFER",
        jdbc.queryForList(
            "SELECT id FROM campus_trade_offer WHERE merchant_id=?", Long.class, store));
    var orders =
        jdbc.queryForList("SELECT id FROM campus_order WHERE merchant_id=?", Long.class, store);
    for (long order : orders) {
      jdbc.update(
          "DELETE n FROM campus_notification n JOIN campus_outbox_event e ON e.id=n.event_id WHERE"
              + " e.aggregate_id=? AND e.event_type LIKE 'ORDER_%'",
          "" + order);
      jdbc.update(
          "DELETE FROM campus_outbox_event WHERE aggregate_id=? AND event_type LIKE 'ORDER_%'",
          "" + order);
      jdbc.update(
          "DELETE c FROM campus_trade_callback c JOIN campus_trade_payment p ON p.id=c.payment_id"
              + " WHERE p.order_id=?",
          order);
      for (String table :
          List.of(
              "campus_trade_payment",
              "campus_trade_refund",
              "campus_trade_cancellation",
              "campus_trade_order_request",
              "campus_trade_group_member"))
        jdbc.update("DELETE FROM " + table + " WHERE order_id=?", order);
      jdbc.update("DELETE FROM campus_order WHERE id=?", order);
    }
    jdbc.update(
        "DELETE g FROM campus_trade_group g JOIN campus_trade_offer o ON o.id=g.offer_id WHERE"
            + " o.merchant_id=?",
        store);
    jdbc.update("DELETE FROM campus_trade_offer WHERE merchant_id=?", store);
    jdbc.update("DELETE FROM campus_merchant_member WHERE merchant_id=?", store);
    jdbc.update("DELETE FROM campus_merchant WHERE id=?", store);
    for (long user : users) {
      jdbc.update("DELETE FROM campus_audit_event WHERE actor_id=?", user);
      jdbc.update("DELETE FROM campus_auth_session WHERE user_id=?", user);
      jdbc.update("DELETE FROM campus_file_object WHERE owner_id=?", user);
      jdbc.update("DELETE FROM campus_user WHERE id=?", user);
    }
    jdbc.update("DELETE FROM campus_school WHERE id=?", school);
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock tradeClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant now = Instant.now();

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}
