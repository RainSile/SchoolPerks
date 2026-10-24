package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.security.port.OperationAuditPort;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;

/** Committed, isolated SQL fixtures: real views, guards, cache and controller AOP. No load test. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.analytics.allowed-zones=Asia/Shanghai,UTC,America/New_York",
      "campus.notifications.dispatch-enabled=false",
      "campus.trade.scheduler-enabled=false",
      "campus.search.rebuild-enabled=false"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
class AnalyticsStageSevenMySqlTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenIssuer issuer;
  @MockitoSpyBean OperationAuditPort audit;
  final List<Long> users = new ArrayList<>(),
      stores = new ArrayList<>(),
      coupons = new ArrayList<>(),
      offers = new ArrayList<>(),
      orders = new ArrayList<>();
  final List<Long> roles = new ArrayList<>();
  long school,
      owner,
      manager,
      cashier,
      other,
      user,
      user2,
      admin,
      unprivileged,
      merchant,
      foreign,
      coupon,
      offer;
  String auth;
  static final Instant START = Instant.parse("2024-06-01T16:00:00Z"),
      END = Instant.parse("2024-06-03T16:00:00Z");

  static String secret() {
    byte[] b = new byte[32];
    new java.security.SecureRandom().nextBytes(b);
    return Base64.getEncoder().encodeToString(b);
  }

  static final String JWT = secret(), OTP = secret(), IDENTITY = secret();

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) {
    r.add("campus.security.jwt.secret-base64", () -> JWT);
    r.add("campus.auth.code-hash-secret-base64", () -> OTP);
    r.add("campus.auth.identity-key-base64", () -> IDENTITY);
  }

  long insert(String sql, Object... values) {
    var key = new GeneratedKeyHolder();
    db.update(
        c -> {
          var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
          return p;
        },
        key);
    return key.getKey().longValue();
  }

  Timestamp ts(Instant t) {
    return t == null ? null : Timestamp.from(t);
  }

  long account(String type) {
    long id =
        insert(
            "INSERT INTO campus_user(phone,nickname,account_type) VALUES(?,'analytics-test',?)",
            "an-" + UUID.randomUUID().toString().substring(0, 20),
            type);
    users.add(id);
    return id;
  }

  void member(long store, long account, String role) {
    db.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,?)",
        store,
        account,
        role);
  }

  long store(long account) {
    long id =
        insert(
            "INSERT INTO campus_merchant(owner_id,school_id,name,category,address,status)"
                + " VALUES(?,?,'analytics-store','DINING','fixture','APPROVED')",
            account,
            school);
    stores.add(id);
    member(id, account, "OWNER");
    return id;
  }

  long coupon(long store) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_coupon(merchant_id,title,coupon_type,rules,total_stock,remaining_stock,valid_from,valid_until)"
                + " VALUES(?,'analytics-coupon','AMOUNT_OFF','{}',100,90,?,?)",
            store,
            ts(START.minusSeconds(86400)),
            ts(END.plusSeconds(86400)));
    coupons.add(id);
    return id;
  }

  long offer(long store) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_trade_offer(merchant_id,title,description,kind,price,stock_total,stock_available,starts_at,ends_at,created_by,created_at)"
                + " VALUES(?,'analytics-offer','fixture','REGULAR',10,100,90,?,?,?,?)",
            store,
            ts(START.minusSeconds(86400)),
            ts(END.plusSeconds(86400)),
            owner,
            ts(START));
    offers.add(id);
    return id;
  }

  long claim(long cp, long account, Instant time, Instant redeemed, long store) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_coupon_claim(coupon_id,user_id,redemption_code_hash,status,claimed_at,redeemed_at)"
                + " VALUES(?,?,?, ?,?,?)",
            cp,
            account,
            UUID.randomUUID().toString().replace("-", "") + "a".repeat(32),
            redeemed == null ? "AVAILABLE" : "REDEEMED",
            ts(time),
            ts(redeemed));
    if (redeemed != null)
      db.update(
          "INSERT INTO campus_redemption(claim_id,merchant_id,operator_id,redeemed_at)"
              + " VALUES(?,?,?,?)",
          id,
          store,
          owner,
          ts(redeemed));
    return id;
  }

  long order(
      long store,
      long of,
      long account,
      String amount,
      Instant created,
      Instant paid,
      String status) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_order(order_no,user_id,merchant_id,amount,currency,offer_snapshot,status,expires_at,created_at,paid_at,offer_id)"
                + " VALUES(?,?,?,?,'CNY','{}',?,?,?,?,?)",
            UUID.randomUUID().toString(),
            account,
            store,
            new java.math.BigDecimal(amount),
            status,
            ts(END.plusSeconds(86400)),
            ts(created),
            ts(paid),
            of);
    orders.add(id);
    return id;
  }

  void refund(long order, String amount, String status, Instant completed) {
    db.update(
        "INSERT INTO"
            + " campus_trade_refund(id,order_id,amount,reason,status,next_attempt_at,created_at,completed_at)"
            + " VALUES(?,?,?,'TEST',?,?,?,?)",
        UUID.randomUUID().toString(),
        order,
        new java.math.BigDecimal(amount),
        status,
        ts(START),
        ts(START),
        ts(completed));
  }

  long grant(long account, String... codes) {
    long role =
        insert(
            "INSERT INTO campus_role(code,name,account_type) VALUES(?,'analytics-fixture','ADMIN')",
            "AN_" + UUID.randomUUID());
    roles.add(role);
    for (String code : codes)
      db.update(
          "INSERT INTO campus_role_permission(role_id,permission_id) SELECT ?,id FROM"
              + " campus_permission WHERE code=?",
          role,
          code);
    db.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", account, role);
    return role;
  }

  String token(long account) {
    var now = Instant.now();
    String sid = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version) SELECT"
            + " ?,id,?,?,access_version FROM campus_user WHERE id=?",
        sid,
        ts(now),
        ts(now.plusSeconds(3600)),
        account);
    return "Bearer "
        + issuer.issue(
            "" + account,
            sid,
            List.of("analytics:read", "merchant:analytics:read", "PORTAL_ADMIN"),
            now,
            now.plusSeconds(1800));
  }

  @BeforeEach
  void setup() {
    school = insert("INSERT INTO campus_school(name) VALUES(?)", "an-" + UUID.randomUUID());
    owner = account("MERCHANT");
    manager = account("MERCHANT");
    cashier = account("MERCHANT");
    other = account("MERCHANT");
    user = account("USER");
    user2 = account("USER");
    admin = account("ADMIN");
    unprivileged = account("ADMIN");
    merchant = store(owner);
    foreign = store(other);
    member(merchant, manager, "MANAGER");
    member(merchant, cashier, "CASHIER");
    coupon = coupon(merchant);
    offer = offer(merchant);
    grant(admin, "analytics:read", "audit:read", "rbac:manage");
    auth = token(owner);
  }

  String path(long store) {
    return "/api/v1/merchants/" + store + "/dashboard";
  }

  ResultActions query(String path, String token, boolean refresh) throws Exception {
    return mvc.perform(
        get(path)
            .header("Authorization", token)
            .param("from", "2024-06-02")
            .param("to", "2024-06-03")
            .param("zone", "Asia/Shanghai")
            .param("refresh", "" + refresh));
  }

  JsonNode body(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsByteArray());
  }

  JsonNode report() throws Exception {
    return body(
        query(path(merchant), auth, true)
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store")));
  }

  @Test
  void computesEventTimeGrossMoneyAndCohortWithoutJoinFanout() throws Exception {
    claim(coupon, user, START, START.plusSeconds(3600), merchant);
    claim(coupon, user2, START.plusSeconds(86400), null, merchant);
    claim(coupon(merchant), user, START.minusMillis(1), START.plusSeconds(86401), merchant);
    long refunded = order(merchant, offer, user, "12.34", START, START.plusSeconds(10), "REFUNDED");
    refund(refunded, "12.34", "SUCCEEDED", START.plusSeconds(20));
    order(merchant, offer, user, "7.66", START, START.plusSeconds(20), "PAID");
    long pending = order(merchant, offer, user, "5.00", START, null, "REFUND_PENDING");
    refund(pending, "5.00", "PENDING", null);
    long old =
        order(
            merchant,
            offer,
            user,
            "30.00",
            START.minusSeconds(86400),
            START.minusSeconds(86000),
            "REFUNDED");
    refund(old, "30.00", "SUCCEEDED", START.plusSeconds(30));
    order(foreign, offer(foreign), user, "999.00", START, START, "PAID");
    var r = report();
    var m = r.path("metrics");
    assertThat(m.path("claimCount").asInt()).isEqualTo(2);
    assertThat(m.path("couponRedemptionCount").asInt()).isEqualTo(2);
    assertThat(m.path("cohortRedemptionCount").asInt()).isEqualTo(1);
    assertThat(m.path("cohortRedemptionRate").asText()).isEqualTo("0.5000");
    assertThat(m.path("paidAmount").isTextual()).isTrue();
    assertThat(m.path("paidAmount").asText()).isEqualTo("20.00");
    assertThat(m.path("refundAmount").asText()).isEqualTo("42.34");
    assertThat(m.path("netCashFlow").asText()).isEqualTo("-22.34");
    assertThat(m.path("createdOrderCount").asInt()).isEqualTo(3);
    assertThat(m.path("participatingUserCount").asInt()).isEqualTo(2);
    assertThat(r.path("daily")).hasSize(2);
    assertThat(r.path("daily").get(0).path("paidAmount").asText()).isEqualTo("20.00");
    assertThat(r.path("topCoupons")).hasSize(1);
    assertThat(r.path("topCoupons").get(0).path("claimCount").asInt()).isEqualTo(2);
    assertThat(r.path("pageViewUv").isNull()).isTrue();
    assertThat(r.path("pageViewUvAvailable").asBoolean()).isFalse();
    assertThat(r.path("paymentEnvironment").asText()).isEqualTo("ALIPAY_SANDBOX");
    assertThat(r.path("newAccounts").isNull()).isTrue();
  }

  @Test
  void respectsInclusiveLocalDatesHalfOpenUtcBoundariesAndZeroDays() throws Exception {
    claim(coupon, user, START, null, merchant);
    claim(coupon, user2, END, null, merchant);
    var r = report();
    assertThat(r.path("startsAt").asText()).isEqualTo(START.toString());
    assertThat(r.path("endsAt").asText()).isEqualTo(END.toString());
    assertThat(r.path("metrics").path("claimCount").asInt()).isEqualTo(1);
    assertThat(r.path("daily").get(1).path("claimCount").asInt()).isZero();
    var empty =
        body(
            mvc.perform(
                    get(path(merchant))
                        .header("Authorization", auth)
                        .param("from", "2024-06-06")
                        .param("to", "2024-06-07"))
                .andExpect(status().isOk()));
    assertThat(empty.path("daily")).hasSize(2);
    assertThat(empty.path("metrics").path("cohortRedemptionRate").isNull()).isTrue();
  }

  @Test
  void daylightSavingUsesZoneRulesWithoutMysqlTimeZoneTables() throws Exception {
    var r =
        body(
            mvc.perform(
                    get(path(merchant))
                        .header("Authorization", auth)
                        .param("from", "2024-03-10")
                        .param("to", "2024-03-10")
                        .param("zone", "America/New_York"))
                .andExpect(status().isOk()));
    assertThat(
            Duration.between(
                Instant.parse(r.path("startsAt").asText()),
                Instant.parse(r.path("endsAt").asText())))
        .isEqualTo(Duration.ofHours(23));
  }

  @Test
  void validatesBoundsAndLegacyEntryRequiresExplicitStore() throws Exception {
    for (String[] values :
        List.of(
            new String[] {"2024-06-03", "2024-06-02"},
            new String[] {"2024-01-01", "2024-06-02"},
            new String[] {"9999-01-01", "9999-01-02"}))
      mvc.perform(
              get(path(merchant))
                  .header("Authorization", auth)
                  .param("from", values[0])
                  .param("to", values[1]))
          .andExpect(status().isBadRequest());
    mvc.perform(get(path(merchant)).header("Authorization", auth).param("from", "2024-06-02"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path(merchant)).header("Authorization", auth).param("zone", "Europe/London"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/merchant/dashboard").header("Authorization", auth))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/merchant/dashboard")
                .header("Authorization", auth)
                .param("merchantId", "" + merchant))
        .andExpect(status().isOk());
  }

  @Test
  void separatesPortalsAndMembersAndDoesNotTrustForgedJwtAuthorities() throws Exception {
    query(path(merchant), token(user), false).andExpect(status().isForbidden());
    query(path(merchant), token(admin), false).andExpect(status().isForbidden());
    query(path(merchant), token(cashier), false).andExpect(status().isForbidden());
    query(path(merchant), token(other), false).andExpect(status().isNotFound());
    query(path(merchant), token(manager), false).andExpect(status().isOk());
    query("/api/v1/admin/dashboard", token(unprivileged), false).andExpect(status().isForbidden());
    query("/api/v1/admin/dashboard", auth, false).andExpect(status().isForbidden());
    query("/api/v1/admin/dashboard", token(admin), true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("PLATFORM"));
    mvc.perform(get("/api/v1/users/me").header("Authorization", token(cashier)))
        .andExpect(
            jsonPath(
                "$.permissions",
                org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.hasItem("merchant:analytics:read"))));
    mvc.perform(get("/api/v1/users/me").header("Authorization", auth))
        .andExpect(
            jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("merchant:analytics:read")));
  }

  @Test
  void cacheIsScopedAndRefreshesButCannotOutliveMembershipOrSession() throws Exception {
    assertThat(report().path("metrics").path("claimCount").asInt()).isZero();
    claim(coupon, user, START, null, merchant);
    assertThat(body(query(path(merchant), auth, false)).path("metrics").path("claimCount").asInt())
        .isZero();
    assertThat(report().path("metrics").path("claimCount").asInt()).isEqualTo(1);
    assertThat(
            body(query(path(foreign), token(other), false))
                .path("metrics")
                .path("claimCount")
                .asInt())
        .isZero();
    db.update(
        "UPDATE campus_merchant_member SET member_role='CASHIER' WHERE merchant_id=? AND user_id=?",
        merchant,
        owner);
    query(path(merchant), auth, false).andExpect(status().isForbidden());
    db.update(
        "UPDATE campus_merchant_member SET member_role='OWNER' WHERE merchant_id=? AND user_id=?",
        merchant,
        owner);
    db.update("UPDATE campus_auth_session SET revoked_at=UTC_TIMESTAMP(3) WHERE user_id=?", owner);
    query(path(merchant), auth, false).andExpect(status().isUnauthorized());
  }

  @Test
  void pausedStoreRetainsHistoricalStatistics() throws Exception {
    claim(coupon, user, START, null, merchant);
    db.update("UPDATE campus_merchant SET status='SUSPENDED' WHERE id=?", merchant);
    assertThat(report().path("metrics").path("claimCount").asInt()).isEqualTo(1);
    member(foreign, owner, "MANAGER");
    query(path(foreign), auth, true).andExpect(status().isOk());
    // Another eligible store keeps the global menu permission; each target still needs its own
    // guard.
    for (String state : List.of("PENDING", "REJECTED")) {
      db.update("UPDATE campus_merchant SET status=? WHERE id=?", state, foreign);
      query(path(foreign), auth, false).andExpect(status().isForbidden());
    }
  }

  @Test
  void platformSumsStoresAndLosesAccessImmediatelyAfterRoleRevocation() throws Exception {
    claim(coupon, user, START, null, merchant);
    claim(coupon(foreign), user, START, null, foreign);
    String token = token(admin);
    var r = body(query("/api/v1/admin/dashboard", token, true).andExpect(status().isOk()));
    assertThat(r.path("metrics").path("claimCount").asInt()).isEqualTo(2);
    assertThat(r.path("metrics").path("participatingUserCount").asInt()).isEqualTo(1);
    assertThat(r.path("newAccounts").isNumber()).isTrue();
    db.update("DELETE FROM campus_user_role WHERE user_id=?", admin);
    query("/api/v1/admin/dashboard", token, false).andExpect(status().isForbidden());
  }

  @Test
  void aopPersistsSuccessDenialAndFailureWithoutSensitivePayloads() throws Exception {
    report();
    query(path(merchant), token(cashier), false).andExpect(status().isForbidden());
    query(path(foreign), auth, false).andExpect(status().isNotFound());
    mvc.perform(
            get(path(merchant))
                .header("Authorization", auth)
                .param("zone", "private-untrusted-value"))
        .andExpect(status().isBadRequest());
    var ledger =
        db.queryForList(
            "SELECT * FROM campus_operation_audit WHERE actor_id IN (?,?)", owner, cashier);
    assertThat(ledger).extracting(r -> r.get("outcome")).contains("SUCCESS", "DENIED", "FAILURE");
    assertThat(ledger.toString())
        .doesNotContain("private-untrusted-value", "Bearer ", auth, "phone", "password", "stack");
    assertThat(ledger)
        .allSatisfy(r -> assertThat(((Number) r.get("duration_ms")).longValue()).isNotNegative());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_audit_event WHERE actor_id=?", Long.class, owner))
        .isZero();
  }

  @Test
  void operationalSinkFailureDoesNotTurnSuccessfulRequestIntoError() throws Exception {
    OperationAuditPort target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
    doThrow(new IllegalStateException("private-sink-error")).when(target).record(any());
    report();
  }

  @Test
  void queriesOperationalAuditWithFiltersAndRequiresAuditPermission() throws Exception {
    report();
    String adminToken = token(admin);
    mvc.perform(
            get("/api/v1/admin/operation-audit-events")
                .header("Authorization", adminToken)
                .param("actorId", "" + owner)
                .param("action", "MERCHANT_DASHBOARD_QUERIED")
                .param("outcome", "SUCCESS"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(
            get("/api/v1/admin/operation-audit-events")
                .header("Authorization", token(unprivileged)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/admin/operation-audit-events")
                .header("Authorization", adminToken)
                .param("from", "2024-01-01T00:00:00Z")
                .param("to", "2025-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/admin/operation-audit-events")
                .header("Authorization", adminToken)
                .param("outcome", "unknown"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rbacWriteKeepsAtomicBusinessAuditAndAddsOperationalResult() throws Exception {
    String code = "AN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    var created =
        body(
            mvc.perform(
                    post("/api/v1/admin/roles")
                        .header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsBytes(
                                Map.of(
                                    "code",
                                    code,
                                    "name",
                                    "private-role-name",
                                    "accountType",
                                    "ADMIN",
                                    "permissions",
                                    List.of("analytics:read")))))
                .andExpect(status().isCreated()));
    long role = created.path("id").asLong();
    roles.add(role);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_audit_event WHERE actor_id=?", Long.class, admin))
        .isEqualTo(1);
    var result =
        db.queryForMap(
            "SELECT outcome,http_status,resource_id FROM campus_operation_audit WHERE actor_id=?"
                + " AND action='RBAC_ROLE_CREATE'",
            admin);
    assertThat(result.get("outcome")).isEqualTo("SUCCESS");
    assertThat(((Number) result.get("http_status")).intValue()).isEqualTo(201);
    assertThat(result.get("resource_id")).isEqualTo("COLLECTION");
  }

  @AfterEach
  void cleanup() {
    // Only exact IDs created by this test; never truncate/reset or touch demo data.
    for (long order : orders) {
      db.update("DELETE FROM campus_trade_refund WHERE order_id=?", order);
      db.update("DELETE FROM campus_order WHERE id=?", order);
    }
    for (long cp : coupons) {
      db.update(
          "DELETE FROM campus_redemption WHERE claim_id IN (SELECT id FROM campus_coupon_claim"
              + " WHERE coupon_id=?)",
          cp);
      db.update("DELETE FROM campus_coupon_claim WHERE coupon_id=?", cp);
      db.update("DELETE FROM campus_coupon WHERE id=?", cp);
    }
    for (long of : offers) db.update("DELETE FROM campus_trade_offer WHERE id=?", of);
    for (long account : users) {
      db.update("DELETE FROM campus_operation_audit WHERE actor_id=?", account);
      db.update("DELETE FROM campus_audit_event WHERE actor_id=?", account);
      db.update("DELETE FROM campus_auth_session WHERE user_id=?", account);
      db.update("DELETE FROM campus_user_role WHERE user_id=?", account);
    }
    for (long role : roles) {
      db.update("DELETE FROM campus_role_permission WHERE role_id=?", role);
      db.update("DELETE FROM campus_role WHERE id=?", role);
    }
    for (long store : stores) {
      db.update("DELETE FROM campus_merchant_member WHERE merchant_id=?", store);
      db.update("DELETE FROM campus_merchant WHERE id=?", store);
    }
    for (long account : users) db.update("DELETE FROM campus_user WHERE id=?", account);
    if (school != 0) db.update("DELETE FROM campus_school WHERE id=?", school);
  }
}
