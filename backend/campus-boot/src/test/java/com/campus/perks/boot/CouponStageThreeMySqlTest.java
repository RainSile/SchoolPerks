package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.*;
import com.campus.perks.notification.serviceImpl.*;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.campus.perks.user.service.AuthSecrets;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;

@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.coupon.max-stock=5",
      "campus.coupon.max-validity=2d",
      "campus.coupon.ticket-ttl=30s",
      "campus.coupon.ticket-resend-interval=2s",
      "campus.coupon.redemption-window=10s",
      "campus.coupon.redemption-window-limit=3",
      "campus.notifications.dispatch-enabled=false",
      "campus.notifications.max-attempts=2",
      "campus.notifications.retry-base=1s",
      "campus.notifications.retry-max=2s"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
@Transactional
@Import(CouponStageThreeMySqlTest.TimeConfiguration.class)
class CouponStageThreeMySqlTest {
  private static final AtomicLong PHONES = new AtomicLong(40000000);

  private static String key() {
    byte[] value = new byte[32];
    new SecureRandom().nextBytes(value);
    return Base64.getEncoder().encodeToString(value);
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
  @Autowired AuthSecrets secrets;
  @Autowired MutableClock clock;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired OutboxQueue queue;
  @Autowired NotificationSocket socket;
  @Autowired MerchantAccessPort merchantAccess;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @LocalServerPort int port;
  @MockitoSpyBean EventPublisherPort publisher;
  @MockitoSpyBean AuditPort audit;
  @MockitoSpyBean OutboxPort outbox;
  private long school,
      owner,
      otherOwner,
      cashier,
      reviewer,
      buyer,
      otherBuyer,
      merchant,
      otherMerchant;
  private final List<Long> users = new ArrayList<>(),
      roles = new ArrayList<>(),
      permissions = new ArrayList<>();

  private long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
          return ps;
        },
        keys);
    return keys.getKey().longValue();
  }

  private long user() {
    String phone;
    do {
      phone = "139" + PHONES.incrementAndGet();
    } while (jdbc.queryForObject(
            "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, phone)
        > 0);
    long id =
        insert(
            "INSERT INTO campus_user(phone,nickname) VALUES(?,?)",
            phone,
            "stage3-test-" + UUID.randomUUID());
    users.add(id);
    return id;
  }

  private void grant(long user, String code) {
    Long permission =
        jdbc.query(
            "SELECT id FROM campus_permission WHERE code=?",
            rs -> rs.next() ? rs.getLong(1) : null,
            code);
    if (permission == null) {
      permission =
          insert("INSERT INTO campus_permission(code,name) VALUES(?,?)", code, "stage3-test");
      permissions.add(permission);
    }
    long role =
        insert(
            "INSERT INTO campus_role(code,name) VALUES(?,?)",
            UUID.randomUUID().toString(),
            "stage3-test");
    roles.add(role);
    jdbc.update(
        "INSERT INTO campus_role_permission(role_id,permission_id) VALUES(?,?)", role, permission);
    jdbc.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", user, role);
  }

  private long store(long owner) {
    String file = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at,completed_at)"
            + " VALUES(?,?,'MERCHANT_QUALIFICATION','test.pdf','application/pdf',64,?,?,'READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
            + " 1 HOUR),UTC_TIMESTAMP(3))",
        file,
        owner,
        "stage3/pending/" + file,
        "stage3/sealed/" + file);
    long store =
        insert(
            "INSERT INTO"
                + " campus_merchant(owner_id,school_id,name,category,address,status,qualification_file_id)"
                + " VALUES(?,?,?,'DINING','stage3-test-address','APPROVED',?)",
            owner,
            school,
            "stage3-test-store-" + UUID.randomUUID(),
            file);
    jdbc.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'OWNER')",
        store,
        owner);
    return store;
  }

  @BeforeEach
  void fixtures() {
    clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    school =
        insert("INSERT INTO campus_school(name) VALUES(?)", "stage3-test-" + UUID.randomUUID());
    owner = user();
    otherOwner = user();
    cashier = user();
    reviewer = user();
    buyer = user();
    otherBuyer = user();
    jdbc.update(
        "UPDATE campus_user SET account_type='MERCHANT' WHERE id IN (?,?,?)",
        owner,
        otherOwner,
        cashier);
    jdbc.update("UPDATE campus_user SET account_type='ADMIN' WHERE id=?", reviewer);
    merchant = store(owner);
    otherMerchant = store(otherOwner);
    jdbc.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'CASHIER')",
        merchant,
        cashier);
    grant(reviewer, "coupon:review");
    grant(reviewer, "notification:manage");
  }

  private String token(long user) {
    var now = clock.instant();
    String sid = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version)"
            + " VALUES(?,?,?,?,(SELECT access_version FROM campus_user WHERE id=?))",
        sid,
        user,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now.plus(Duration.ofDays(4))),
        user);
    return "Bearer "
        + issuer.issue(Long.toString(user), sid, List.of(), now, now.plus(Duration.ofMinutes(15)));
  }

  private ResultActions post(String path, long actor, Object value) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1" + path)
            .header("Authorization", token(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(value)));
  }

  private ResultActions get(String path, long actor) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1" + path)
            .header("Authorization", token(actor)));
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private Map<String, Object> content(int stock) {
    return new HashMap<>(
        Map.of(
            "title",
            "stage3-test-offer",
            "couponType",
            "AMOUNT_OFF",
            "rules",
            Map.of("threshold", 50, "amountOff", 10),
            "totalStock",
            stock,
            "validFrom",
            clock.instant().minusSeconds(60),
            "validUntil",
            clock.instant().plusSeconds(3600),
            "campusOnly",
            false,
            "description",
            "stage3-test-description"));
  }

  private String create(int stock) throws Exception {
    return body(post("/merchants/" + merchant + "/coupons", owner, content(stock))
            .andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  private int version(String id) throws Exception {
    return body(get("/coupons/" + id + "/management", owner)).get("version").asInt();
  }

  private ResultActions patchCoupon(String id, int version, Map<String, Object> input)
      throws Exception {
    return mvc.perform(
        patch("/api/v1/coupons/" + id)
            .header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("version", version, "content", input))));
  }

  private String sendReview(String id) throws Exception {
    return body(post("/coupons/" + id + "/review-requests", owner, Map.of("version", version(id)))
            .andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  private String publish(int stock) throws Exception {
    return publish(create(stock));
  }

  private String publish(String id) throws Exception {
    String request = sendReview(id);
    post(
            "/admin/coupon-review-requests/" + request + "/reviews",
            reviewer,
            Map.of("decision", "APPROVED", "reason", "stage3 test approval"))
        .andExpect(status().isCreated());
    change(id, "PUBLISHED").andExpect(status().isCreated());
    return id;
  }

  private ResultActions change(String id, String state) throws Exception {
    return post(
        "/coupons/" + id + "/status-changes",
        owner,
        Map.of("version", version(id), "status", state, "reason", "stage3 test state"));
  }

  private String claim(String id, long actor) throws Exception {
    return body(post("/coupons/" + id + "/claims", actor, Map.of()).andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  private String ticket(String id, long actor) throws Exception {
    var response =
        post("/coupon-claims/" + id + "/redemption-tickets", actor, Map.of())
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));
    return body(response).get("code").asText();
  }

  private ResultActions redeem(long store, String code, long actor) throws Exception {
    return post("/redemptions", actor, Map.of("merchantId", Long.toString(store), "code", code));
  }

  @Test
  void createAndPublishEnforceMembershipReviewRulesAndWhitelist() throws Exception {
    post("/merchants/" + merchant + "/coupons", cashier, content(1))
        .andExpect(status().isForbidden());
    post("/merchants/" + merchant + "/coupons", otherOwner, content(1))
        .andExpect(status().isNotFound());
    post("/merchants/" + merchant + "/coupons", owner, content(6))
        .andExpect(status().isBadRequest());
    var invalid = content(1);
    invalid.put("rules", Map.of("threshold", 10, "amountOff", 20));
    post("/merchants/" + merchant + "/coupons", owner, invalid).andExpect(status().isBadRequest());
    invalid = content(1);
    invalid.put("status", "PUBLISHED");
    post("/merchants/" + merchant + "/coupons", owner, invalid).andExpect(status().isBadRequest());
    String id = create(1);
    change(id, "PUBLISHED").andExpect(status().isConflict());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/coupons/" + id))
        .andExpect(status().isNotFound());
    get("/coupons/" + id + "/management", otherOwner).andExpect(status().isNotFound());
    jdbc.update("UPDATE campus_merchant SET status='SUSPENDED' WHERE id=?", merchant);
    post("/merchants/" + merchant + "/coupons", owner, content(1)).andExpect(status().isConflict());
  }

  @Test
  void reviewIsImmutableScopedAndCannotApproveSelfOrBeReusedAfterEditing() throws Exception {
    String id = create(2), request = sendReview(id);
    patchCoupon(id, version(id), content(1)).andExpect(status().isConflict());
    get("/admin/coupon-review-requests", buyer).andExpect(status().isForbidden());
    grant(owner, "coupon:review");
    post(
            "/admin/coupon-review-requests/" + request + "/reviews",
            owner,
            Map.of("decision", "APPROVED", "reason", "self"))
        .andExpect(status().isForbidden());
    var record =
        body(
            post(
                    "/admin/coupon-review-requests/" + request + "/reviews",
                    reviewer,
                    Map.of("decision", "REJECTED", "reason", "revise description"))
                .andExpect(status().isCreated()));
    get("/admin/coupon-reviews/" + record.get("id").asText(), reviewer).andExpect(status().isOk());
    post(
            "/admin/coupon-review-requests/" + request + "/reviews",
            reviewer,
            Map.of("decision", "APPROVED", "reason", "duplicate"))
        .andExpect(status().isConflict());
    get("/coupons/" + id + "/management", owner)
        .andExpect(jsonPath("$.reviewReason").value("revise description"));
    get("/coupon-review-requests/" + request, owner)
        .andExpect(jsonPath("$.review.reason").value("revise description"));
    int old = version(id);
    var revised = content(2);
    revised.put("title", "revised title");
    patchCoupon(id, old, revised).andExpect(status().isOk());
    patchCoupon(id, old, revised).andExpect(status().isConflict());
    get("/coupon-review-requests/" + request, owner)
        .andExpect(jsonPath("$.content.title").value("stage3-test-offer"));
    get("/coupon-review-requests/" + request, otherOwner).andExpect(status().isNotFound());
    publish(id);
    patchCoupon(id, version(id), content(3)).andExpect(status().isConflict());
  }

  @Test
  void publicCatalogFiltersAndHidesPausedExpiredAndOfflineOffers() throws Exception {
    String id = publish(2);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/coupons")
                .param("schoolId", Long.toString(school))
                .param("keyword", "stage3-test-offer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/coupons")
                .param("merchantId", Long.toString(otherMerchant)))
        .andExpect(jsonPath("$.total").value(0));
    jdbc.update("UPDATE campus_merchant SET status='SUSPENDED' WHERE id=?", merchant);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/coupons/" + id))
        .andExpect(status().isNotFound());
    post("/coupons/" + id + "/claims", buyer, Map.of()).andExpect(status().isConflict());
    jdbc.update("UPDATE campus_merchant SET status='APPROVED' WHERE id=?", merchant);
    change(id, "OFFLINE").andExpect(status().isCreated());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/coupons/" + id))
        .andExpect(status().isNotFound());
    post("/coupons/" + id + "/claims", buyer, Map.of()).andExpect(status().isConflict());
    change(id, "PUBLISHED").andExpect(status().isConflict());
  }

  @Test
  void repeatedClaimReturnsSameResourceAndOnlyDecrementsOnce() throws Exception {
    String id = publish(2), claim = claim(id, buyer);
    post("/coupons/" + id + "/claims", buyer, Map.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(claim));
    assertThat(
            jdbc.queryForObject(
                "SELECT remaining_stock FROM campus_coupon WHERE id=?", Integer.class, id))
        .isEqualTo(1);
    get("/coupon-claims/" + claim, otherBuyer).andExpect(status().isNotFound());
    get("/coupon-claims/" + claim, buyer)
        .andExpect(jsonPath("$.redemptionCodeHash").doesNotExist())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  void campusOnlyOffersRequireApprovedIdentityForTheStoresSchool() throws Exception {
    var input = content(2);
    input.put("campusOnly", true);
    String id =
        publish(
            body(post("/merchants/" + merchant + "/coupons", owner, input)
                    .andExpect(status().isCreated()))
                .get("id")
                .asText());
    post("/coupons/" + id + "/claims", buyer, Map.of()).andExpect(status().isForbidden());
    jdbc.update("UPDATE campus_user SET school_id=? WHERE id=?", school, buyer);
    post("/coupons/" + id + "/claims", buyer, Map.of()).andExpect(status().isForbidden());
    jdbc.update(
        "INSERT INTO"
            + " campus_campus_verification(id,user_id,school_id,encrypted_identity,status,submitted_at)"
            + " VALUES(?,?,?,?,'APPROVED',?)",
        UUID.randomUUID().toString(),
        buyer,
        school,
        secrets.encryptIdentity(
            Long.toString(buyer), "{\"realName\":\"test\",\"studentNumber\":\"test\"}"),
        java.sql.Timestamp.from(clock.instant()));
    claim(id, buyer);
  }

  @Test
  void ticketsArePrivateRotatableRateLimitedAndStoreOnlyDigest() throws Exception {
    String id = claim(publish(2), buyer);
    post("/coupon-claims/" + id + "/redemption-tickets", otherBuyer, Map.of())
        .andExpect(status().isNotFound());
    var value =
        body(
            post("/coupon-claims/" + id + "/redemption-tickets", buyer, Map.of())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")));
    String first = value.get("code").asText();
    assertThat(Base64.getUrlDecoder().decode(first.substring(first.indexOf('.') + 1))).hasSize(32);
    assertThat(value.get("qrCodeSvg").asText()).startsWith("<svg ").doesNotContain("\\\"");
    assertThat(
            jdbc.queryForObject(
                "SELECT redemption_code_hash FROM campus_coupon_claim WHERE id=?",
                String.class,
                id))
        .hasSize(64)
        .doesNotContain(first);
    post("/coupon-claims/" + id + "/redemption-tickets", buyer, Map.of())
        .andExpect(status().isTooManyRequests());
    clock.advance(Duration.ofSeconds(3));
    String second = ticket(id, buyer);
    assertThat(first).isNotEqualTo(second);
    redeem(merchant, first, cashier).andExpect(status().isBadRequest());
    redeem(merchant, second, cashier).andExpect(status().isCreated());
  }

  @Test
  void offlineStopsClaimsButHonorsExistingCouponsAndRedemptionIsIdempotent() throws Exception {
    String offer = publish(2), claim = claim(offer, buyer);
    change(offer, "OFFLINE").andExpect(status().isCreated());
    String code = ticket(claim, buyer);
    redeem(otherMerchant, code, otherOwner).andExpect(status().isBadRequest());
    redeem(merchant, code, otherBuyer).andExpect(status().isForbidden());
    var first = body(redeem(merchant, code, cashier).andExpect(status().isCreated()));
    redeem(merchant, code, cashier)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(first.get("id").asText()));
    get("/redemptions/" + first.get("id").asText(), owner).andExpect(status().isOk());
    get("/redemptions/" + first.get("id").asText(), otherOwner).andExpect(status().isNotFound());
    get("/coupon-claims/" + claim, buyer).andExpect(jsonPath("$.status").value("REDEEMED"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_outbox_event WHERE aggregate_id=?",
                Integer.class,
                claim))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT payload FROM campus_outbox_event WHERE aggregate_id=?",
                String.class,
                claim))
        .doesNotContain(code);
  }

  @Test
  void expiredCodesAndCouponsAreRejectedWithoutChangingRedemptionState() throws Exception {
    String offer = publish(1), claim = claim(offer, buyer), code = ticket(claim, buyer);
    clock.advance(Duration.ofSeconds(31));
    redeem(merchant, code, cashier).andExpect(status().isBadRequest());
    clock.advance(Duration.ofHours(2));
    get("/coupon-claims/" + claim, buyer).andExpect(jsonPath("$.status").value("EXPIRED"));
    post("/coupon-claims/" + claim + "/redemption-tickets", buyer, Map.of())
        .andExpect(status().isConflict());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/coupons/" + offer))
        .andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_redemption WHERE claim_id=?", Integer.class, claim))
        .isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void invalidRedemptionAttemptsRemainCountedAcrossFailedRequests() throws Exception {
    String bad = "1." + "a".repeat(43);
    for (int i = 0; i < 3; i++) redeem(merchant, bad, cashier).andExpect(status().isBadRequest());
    redeem(merchant, bad, cashier).andExpect(status().isTooManyRequests());
    assertThat(
            jdbc.queryForObject(
                "SELECT attempts FROM campus_redemption_attempt_rate WHERE operator_id=?",
                Integer.class,
                cashier))
        .isEqualTo(3);
    clock.advance(Duration.ofSeconds(11));
    redeem(merchant, bad, cashier).andExpect(status().isBadRequest());
  }

  private List<Integer> concurrent(String path, Object input, long first, long second)
      throws Exception {
    var workers = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    String body = json.writeValueAsString(input), a = token(first), b = token(second);
    try {
      Callable<Integer> one =
          () -> {
            start.await();
            return mvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                            "/api/v1" + path)
                        .header("Authorization", a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      Callable<Integer> two =
          () -> {
            start.await();
            return mvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                            "/api/v1" + path)
                        .header("Authorization", b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var x = workers.submit(one);
      var y = workers.submit(two);
      start.countDown();
      return List.of(x.get(15, TimeUnit.SECONDS), y.get(15, TimeUnit.SECONDS));
    } finally {
      workers.shutdownNow();
      workers.awaitTermination(15, TimeUnit.SECONDS);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentClaimsDoNotOversellAndTheSameUserDoesNotDecrementTwice() throws Exception {
    String one = publish(1);
    assertThat(concurrent("/coupons/" + one + "/claims", Map.of(), buyer, otherBuyer))
        .containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT remaining_stock FROM campus_coupon WHERE id=?", Integer.class, one))
        .isZero();
    String two = publish(2);
    assertThat(concurrent("/coupons/" + two + "/claims", Map.of(), buyer, buyer))
        .containsExactlyInAnyOrder(201, 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT remaining_stock FROM campus_coupon WHERE id=?", Integer.class, two))
        .isEqualTo(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRedemptionsWriteOneReceiptAuditAndOutboxEvent() throws Exception {
    String claim = claim(publish(1), buyer), code = ticket(claim, buyer);
    assertThat(
            concurrent(
                "/redemptions",
                Map.of("merchantId", Long.toString(merchant), "code", code),
                cashier,
                cashier))
        .containsExactlyInAnyOrder(201, 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_redemption WHERE claim_id=?", Integer.class, claim))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_outbox_event WHERE aggregate_id=?",
                Integer.class,
                claim))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_audit_event WHERE resource_id=? AND"
                    + " action='COUPON_REDEEMED'",
                Integer.class,
                claim))
        .isEqualTo(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void outboxFailureRollsBackTheRedemptionAndItsAudit() throws Exception {
    String claim = claim(publish(1), buyer), code = ticket(claim, buyer);
    OutboxPort target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
    doThrow(new IllegalStateException("test-outbox-failure")).when(target).append(any());
    assertThatThrownBy(() -> redeem(merchant, code, cashier))
        .isInstanceOf(jakarta.servlet.ServletException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM campus_coupon_claim WHERE id=?", String.class, claim))
        .isEqualTo("AVAILABLE");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_redemption WHERE claim_id=?", Integer.class, claim))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_audit_event WHERE resource_id=? AND"
                    + " action='COUPON_REDEEMED'",
                Integer.class,
                claim))
        .isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void auditFailureRollsBackClaimInventory() throws Exception {
    String offer = publish(1);
    AuditPort target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
    doThrow(new IllegalStateException("test-audit-failure"))
        .when(target)
        .record(anyString(), eq("COUPON_CLAIMED"), anyString(), anyString(), anyMap());
    assertThatThrownBy(() -> claim(offer, buyer))
        .isInstanceOf(jakarta.servlet.ServletException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT remaining_stock FROM campus_coupon WHERE id=?", Integer.class, offer))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_coupon_claim WHERE coupon_id=?", Integer.class, offer))
        .isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedNotificationsCanRetryWithoutDuplicateInboxAndNeverUndoRedemption() throws Exception {
    String claim = claim(publish(1), buyer);
    redeem(merchant, ticket(claim, buyer), cashier).andExpect(status().isCreated());
    String event =
        jdbc.queryForObject(
            "SELECT id FROM campus_outbox_event WHERE aggregate_id=?", String.class, claim);
    doThrow(new IllegalStateException("test-delivery-failure")).when(publisher).publish(any());
    dispatcher.dispatch();
    clock.advance(Duration.ofSeconds(2));
    dispatcher.dispatch();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM campus_outbox_event WHERE id=?", String.class, event))
        .isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM campus_coupon_claim WHERE id=?", String.class, claim))
        .isEqualTo("REDEEMED");
    mvc.perform(
            put("/api/v1/admin/notification-deliveries/" + event + "/retry-request")
                .header("Authorization", token(owner)))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/admin/notification-deliveries/" + event + "/retry-request")
                .header("Authorization", token(reviewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));
    doCallRealMethod().when(publisher).publish(any());
    dispatcher.dispatch();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM campus_outbox_event WHERE id=?", String.class, event))
        .isEqualTo("PUBLISHED");
    var notice =
        body(get("/users/me/notifications", buyer).andExpect(status().isOk())).get("items").get(0);
    String id = notice.get("id").asText();
    assertThat(notice.toString()).doesNotContain("userId", "code");
    get("/users/me/notifications/" + id, otherBuyer).andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/users/me/notifications/" + id + "/read-receipt")
                .header("Authorization", token(buyer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").isNotEmpty());
    jdbc.update(
        "UPDATE campus_outbox_event SET status='PENDING',attempts=0,next_attempt_at=? WHERE id=?",
        java.sql.Timestamp.from(clock.instant()),
        event);
    dispatcher.dispatch();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_notification WHERE event_id=?", Integer.class, event))
        .isEqualTo(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void expiredWorkerLeaseIsRecoverableAndOldAcknowledgementCannotCompleteNewLease()
      throws Exception {
    String coupon = publish(1);
    clock.advance(Duration.ofSeconds(2));
    dispatcher
        .dispatch(); // Finish the new search projection event before isolating this delivery lease.
    String claim = claim(coupon, buyer);
    redeem(merchant, ticket(claim, buyer), cashier).andExpect(status().isCreated());
    var abandoned = queue.claim();
    assertThat(abandoned).isNotNull();
    assertThat(queue.claim()).isNull();
    clock.advance(Duration.ofMinutes(2));
    var recovered = queue.claim();
    assertThat(recovered.id()).isEqualTo(abandoned.id());
    assertThat(recovered.leaseToken()).isNotEqualTo(abandoned.leaseToken());
    queue.success(abandoned);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM campus_outbox_event WHERE id=?", String.class, recovered.id()))
        .isEqualTo("PROCESSING");
    publisher.publish(
        new EventPublisherPort.Event(
            recovered.id(),
            recovered.eventType(),
            recovered.aggregateId(),
            recovered.createdAt(),
            json.readValue(
                recovered.payload(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})));
    queue.success(recovered);
  }

  @Test
  void discountOffersCanBeBrowsedBeforeStartButCannotBeClaimedUntilActive() throws Exception {
    var input = content(2);
    input.put("couponType", "DISCOUNT");
    input.put("rules", Map.of("threshold", 0, "discountRate", 0.8));
    input.put("validFrom", clock.instant().plusSeconds(60));
    String offer =
        publish(body(post("/merchants/" + merchant + "/coupons", owner, input)).get("id").asText());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/coupons/" + offer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rules.discountRate").value(0.8));
    post("/coupons/" + offer + "/claims", buyer, Map.of()).andExpect(status().isConflict());
    clock.advance(Duration.ofSeconds(61));
    claim(offer, buyer);
    jdbc.update(
        "UPDATE campus_coupon SET review_status='NONE',published_at=NULL WHERE id=?", offer);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/coupons/" + offer))
        .andExpect(status().isNotFound());
    post("/coupons/" + offer + "/claims", otherBuyer, Map.of()).andExpect(status().isConflict());
  }

  private void committedUpdate(Runnable work) throws Exception {
    var executor = Executors.newSingleThreadExecutor();
    try {
      executor.submit(work).get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private org.springframework.security.core.Authentication actor(long user) {
    var jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject(Long.toString(user))
            .build();
    return new org.springframework.security.oauth2.server.resource.authentication
        .JwtAuthenticationToken(jwt, List.of());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void merchantWriteGuardsUseCurrentStateEvenAfterAnOlderRepeatableReadSnapshot() throws Exception {
    var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
    try {
      org.springframework.security.core.context.SecurityContextHolder.getContext()
          .setAuthentication(actor(owner));
      assertThatThrownBy(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        assertThat(
                                jdbc.queryForObject(
                                    "SELECT status FROM campus_merchant WHERE id=?",
                                    String.class,
                                    merchant))
                            .isEqualTo("APPROVED");
                        try {
                          committedUpdate(
                              () ->
                                  jdbc.update(
                                      "UPDATE campus_merchant SET status='SUSPENDED' WHERE id=?",
                                      merchant));
                        } catch (Exception failed) {
                          throw new RuntimeException(failed);
                        }
                        merchantAccess.requirePromotionPublisher(Long.toString(merchant));
                      }))
          .isInstanceOf(com.campus.perks.common.exception.ApplicationException.class)
          .hasMessageContaining("暂停");

      jdbc.update("UPDATE campus_merchant SET status='APPROVED' WHERE id=?", merchant);
      org.springframework.security.core.context.SecurityContextHolder.getContext()
          .setAuthentication(actor(cashier));
      assertThatThrownBy(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        assertThat(
                                jdbc.queryForObject(
                                    "SELECT member_role FROM campus_merchant_member WHERE"
                                        + " merchant_id=? AND user_id=?",
                                    String.class,
                                    merchant,
                                    cashier))
                            .isEqualTo("CASHIER");
                        try {
                          committedUpdate(
                              () ->
                                  jdbc.update(
                                      "DELETE FROM campus_merchant_member WHERE merchant_id=? AND"
                                          + " user_id=?",
                                      merchant,
                                      cashier));
                        } catch (Exception failed) {
                          throw new RuntimeException(failed);
                        }
                        merchantAccess.requireCouponOperator(Long.toString(merchant));
                      }))
          .isInstanceOf(com.campus.perks.common.exception.ApplicationException.class)
          .hasMessageContaining("不存在");
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  private static class SocketListener implements WebSocket.Listener {
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    final CompletableFuture<Integer> closed = new CompletableFuture<>();

    public void onOpen(WebSocket ws) {
      ws.request(1);
    }

    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      messages.add(data.toString());
      ws.request(1);
      return null;
    }

    public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
      closed.complete(status);
      return null;
    }
  }

  private WebSocket connect(SocketListener listener, String origin) throws Exception {
    return HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .header("Origin", origin)
        .connectTimeout(Duration.ofSeconds(5))
        .buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/v1/notification-stream"), listener)
        .get(6, TimeUnit.SECONDS);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void realWebSocketAuthenticatesFirstFrameIsolatesUsersAndClosesRevokedSessions()
      throws Exception {
    var a = new SocketListener();
    var b = new SocketListener();
    var pending = new SocketListener();
    WebSocket first = null, second = null, anonymous = null;
    try {
      assertThatThrownBy(() -> connect(new SocketListener(), "https://untrusted.invalid"))
          .hasCauseInstanceOf(WebSocketHandshakeException.class);
      first = connect(a, "http://localhost:5173");
      second = connect(b, "http://localhost:5173");
      anonymous = connect(pending, "http://localhost:5173");
      first
          .sendText(
              json.writeValueAsString(
                  Map.of("type", "AUTHENTICATE", "accessToken", token(buyer).substring(7))),
              true)
          .join();
      second
          .sendText(
              json.writeValueAsString(
                  Map.of("type", "AUTHENTICATE", "accessToken", token(otherBuyer).substring(7))),
              true)
          .join();
      assertThat(a.messages.poll(5, TimeUnit.SECONDS)).contains("READY");
      assertThat(b.messages.poll(5, TimeUnit.SECONDS)).contains("READY");
      anonymous.sendText("{\"type\":\"PING\"}", true).join();
      assertThat(pending.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
      String claim = claim(publish(1), buyer);
      redeem(merchant, ticket(claim, buyer), cashier).andExpect(status().isCreated());
      dispatcher.dispatch();
      assertThat(a.messages.poll(5, TimeUnit.SECONDS))
          .contains("COUPON_REDEEMED", claim)
          .doesNotContain("accessToken", "redemptionCode");
      assertThat(b.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
      jdbc.update(
          "UPDATE campus_auth_session SET revoked_at=UTC_TIMESTAMP(3) WHERE user_id=?", buyer);
      socket.sweep();
      assertThat(a.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
      clock.advance(Duration.ofMinutes(16));
      socket.sweep();
      assertThat(b.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    } finally {
      if (first != null) first.abort();
      if (second != null) second.abort();
      if (anonymous != null) anonymous.abort();
    }
  }

  @AfterEach
  void cleanup() {
    SearchFixtureCleanup.resources(
        jdbc,
        "COUPON",
        jdbc.queryForList(
            "SELECT id FROM campus_coupon WHERE merchant_id IN (?,?)",
            Long.class,
            merchant,
            otherMerchant));
    if (TestTransaction.isActive() || users.isEmpty()) return;
    String ids = users.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    Object[] values = users.toArray();
    jdbc.update("DELETE FROM campus_notification WHERE user_id IN (" + ids + ")", values);
    jdbc.update(
        "DELETE FROM campus_outbox_event WHERE aggregate_id IN (SELECT CAST(id AS CHAR) FROM"
            + " campus_coupon_claim WHERE user_id IN ("
            + ids
            + "))",
        values);
    jdbc.update("DELETE FROM campus_audit_event WHERE actor_id IN (" + ids + ")", values);
    jdbc.update(
        "DELETE FROM campus_redemption_attempt_rate WHERE operator_id IN (" + ids + ")", values);
    jdbc.update(
        "DELETE FROM campus_redemption WHERE merchant_id IN (?,?)", merchant, otherMerchant);
    jdbc.update(
        "DELETE FROM campus_coupon_claim WHERE coupon_id IN (SELECT id FROM campus_coupon WHERE"
            + " merchant_id IN (?,?))",
        merchant,
        otherMerchant);
    jdbc.update(
        "DELETE FROM campus_coupon_status_change WHERE coupon_id IN (SELECT id FROM campus_coupon"
            + " WHERE merchant_id IN (?,?))",
        merchant,
        otherMerchant);
    jdbc.update(
        "UPDATE campus_coupon SET review_request_id=NULL WHERE merchant_id IN (?,?)",
        merchant,
        otherMerchant);
    jdbc.update(
        "DELETE FROM campus_coupon_review WHERE request_id IN (SELECT id FROM"
            + " campus_coupon_review_request WHERE coupon_id IN (SELECT id FROM campus_coupon WHERE"
            + " merchant_id IN (?,?)))",
        merchant,
        otherMerchant);
    jdbc.update(
        "DELETE FROM campus_coupon_review_request WHERE coupon_id IN (SELECT id FROM campus_coupon"
            + " WHERE merchant_id IN (?,?))",
        merchant,
        otherMerchant);
    jdbc.update("DELETE FROM campus_coupon WHERE merchant_id IN (?,?)", merchant, otherMerchant);
    jdbc.update(
        "DELETE FROM campus_merchant_member WHERE merchant_id IN (?,?)", merchant, otherMerchant);
    jdbc.update("DELETE FROM campus_merchant WHERE id IN (?,?)", merchant, otherMerchant);
    jdbc.update("DELETE FROM campus_file_object WHERE owner_id IN (" + ids + ")", values);
    jdbc.update("DELETE FROM campus_campus_verification WHERE user_id IN (" + ids + ")", values);
    jdbc.update("DELETE FROM campus_auth_session WHERE user_id IN (" + ids + ")", values);
    jdbc.update("DELETE FROM campus_user_role WHERE user_id IN (" + ids + ")", values);
    for (long role : roles) {
      jdbc.update("DELETE FROM campus_role_permission WHERE role_id=?", role);
      jdbc.update("DELETE FROM campus_role WHERE id=?", role);
    }
    for (long permission : permissions)
      jdbc.update(
          "DELETE FROM campus_permission WHERE id=? AND NOT EXISTS(SELECT 1 FROM"
              + " campus_role_permission WHERE permission_id=?)",
          permission,
          permission);
    jdbc.update("DELETE FROM campus_user WHERE id IN (" + ids + ")", values);
    jdbc.update("DELETE FROM campus_school WHERE id=?", school);
  }

  @TestConfiguration
  static class TimeConfiguration {
    @Bean
    @Primary
    MutableClock couponClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant now = Instant.now();

    void advance(Duration d) {
      now = now.plus(d);
    }

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
