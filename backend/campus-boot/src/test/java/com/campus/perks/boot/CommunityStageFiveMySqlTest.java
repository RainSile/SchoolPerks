package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.*;
import com.campus.perks.notification.serviceImpl.*;
import com.campus.perks.search.mapper.SearchMapper;
import com.campus.perks.search.serviceImpl.SearchProjectionService;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.fasterxml.jackson.databind.*;
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
import org.springframework.test.web.servlet.*;

/**
 * Real independent MySQL transactions; exact fixture cleanup, no external service or pressure
 * testing.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.notifications.dispatch-enabled=false",
      "campus.trade.scheduler-enabled=false",
      "campus.search.rebuild-enabled=false",
      "campus.community.blocked-terms=blockme"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
@Import(CommunityStageFiveMySqlTest.TimeConfig.class)
class CommunityStageFiveMySqlTest {
  private static final AtomicLong PHONES = new AtomicLong(61000000);

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
  @Autowired SearchProjectionService projection;
  @Autowired SearchMapper search;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired MutableClock clock;
  @Autowired AccountDirectoryPort accounts;
  @MockitoSpyBean AuditPort audit;
  @MockitoSpyBean OutboxPort outbox;
  private long school, owner, author, other, otherOwner, reviewer, store, otherStore, cashier;
  private final List<Long> users = new ArrayList<>(),
      roles = new ArrayList<>(),
      permissions = new ArrayList<>();
  private final List<String> manualEvents = new ArrayList<>();

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
    long id = insert("INSERT INTO campus_user(phone,nickname) VALUES(?,'stage5-test')", phone);
    users.add(id);
    return id;
  }

  private long store(long user) {
    String file = image(user, "MERCHANT_QUALIFICATION", "READY");
    long id =
        insert(
            "INSERT INTO"
                + " campus_merchant(owner_id,school_id,name,category,address,status,qualification_file_id,longitude,latitude)"
                + " VALUES(?,?,'stage5 noodle','DINING','stage5 east"
                + " gate','APPROVED',?,116.4,39.9)",
            user,
            school,
            file);
    jdbc.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'OWNER')",
        id,
        user);
    return id;
  }

  private String image(long owner, String purpose, String state) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at)"
            + " VALUES(?,?,?,'test.png','image/png',64,?,?,?,UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
            + " 1 HOUR))",
        id,
        owner,
        purpose,
        "stage5/pending/" + id,
        "stage5/sealed/" + id,
        state);
    return id;
  }

  private void grant(long user, String code) {
    Long id =
        jdbc.query(
            "SELECT id FROM campus_permission WHERE code=?",
            r -> r.next() ? r.getLong(1) : null,
            code);
    if (id == null) {
      id = insert("INSERT INTO campus_permission(code,name) VALUES(?,'stage5-test')", code);
      permissions.add(id);
    }
    long role =
        insert(
            "INSERT INTO campus_role(code,name) VALUES(?,'stage5-test')",
            UUID.randomUUID().toString());
    roles.add(role);
    jdbc.update("INSERT INTO campus_role_permission(role_id,permission_id) VALUES(?,?)", role, id);
    jdbc.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", user, role);
  }

  @BeforeEach
  void fixtures() {
    clock.now = Instant.now();
    school = insert("INSERT INTO campus_school(name) VALUES(?)", "stage5-" + UUID.randomUUID());
    owner = user();
    author = user();
    other = user();
    reviewer = user();
    cashier = user();
    otherOwner = user();
    jdbc.update(
        "UPDATE campus_user SET account_type='MERCHANT' WHERE id IN (?,?,?)",
        owner,
        otherOwner,
        cashier);
    jdbc.update("UPDATE campus_user SET account_type='ADMIN' WHERE id=?", reviewer);
    store = store(owner);
    otherStore = store(otherOwner);
    jdbc.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'CASHIER')",
        store,
        cashier);
    grant(reviewer, "community:moderate");
    grant(reviewer, "search:manage");
    grant(author, "community:moderate");
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
        java.sql.Timestamp.from(now.plusSeconds(7200)),
        user);
    return "Bearer " + issuer.issue("" + user, sid, List.of(), now, now.plusSeconds(3600));
  }

  private ResultActions call(String method, String path, long user, Object body) throws Exception {
    var request =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
            org.springframework.http.HttpMethod.valueOf(method), "/api/v1" + path);
    if (user > 0) request.header("Authorization", token(user));
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private Map<String, Object> content(String title, List<String> images) {
    return Map.of("title", title, "body", "noodle 探店 <script>不执行</script>", "imageIds", images);
  }

  private JsonNode draft(String title) throws Exception {
    return body(
        call(
                "POST",
                "/posts",
                author,
                Map.of(
                    "schoolId",
                    "" + school,
                    "merchantId",
                    "" + store,
                    "content",
                    content(title, List.of())))
            .andExpect(status().isCreated()));
  }

  private String submit(JsonNode post) throws Exception {
    return body(call(
                "POST",
                "/posts/" + post.get("id").asText() + "/review-requests",
                author,
                Map.of("version", post.get("version").asInt()))
            .andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  private JsonNode publish() throws Exception {
    var p = draft("stage5 noodle experience");
    String request = submit(p);
    call(
            "POST",
            "/admin/post-review-requests/" + request + "/reviews",
            reviewer,
            Map.of("decision", "APPROVED", "reason", "test approval"))
        .andExpect(status().isCreated());
    return body(call("GET", "/posts/" + p.get("id").asText(), 0, null).andExpect(status().isOk()));
  }

  private void deliver() {
    clock.now = clock.now.plusSeconds(2);
    for (int i = 0; i < 5; i++) dispatcher.dispatch();
  }

  private JsonNode search(String keyword) throws Exception {
    return body(
        mvc.perform(get("/api/v1/search-results").param("keyword", keyword).param("size", "20"))
            .andExpect(status().isOk()));
  }

  private ResultActions comment(String post, long actor, String key, String text) throws Exception {
    return mvc.perform(
        post("/api/v1/posts/" + post + "/comments")
            .header("Authorization", token(actor))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("body", text))));
  }

  private void location(long store, long user, String lon, String lat, int version, String crs)
      throws Exception {
    call(
            "PUT",
            "/merchants/" + store + "/location",
            user,
            Map.of("longitude", lon, "latitude", lat, "coordinateSystem", crs, "version", version))
        .andExpect(status().isOk());
  }

  @Test
  void draftsArePrivateAndClientCannotAssignAuthorOrStatus() throws Exception {
    var p = draft("private noodle");
    call("GET", "/posts/" + p.get("id").asText(), 0, null).andExpect(status().isNotFound());
    call("GET", "/users/me/posts/" + p.get("id").asText(), other, null)
        .andExpect(status().isNotFound());
    var input =
        new HashMap<String, Object>(
            Map.of("schoolId", "" + school, "content", content("title", List.of())));
    input.put("status", "PUBLISHED");
    call("POST", "/posts", author, input).andExpect(status().isBadRequest());
  }

  @Test
  void immutableReviewAndSelfReviewAreEnforced() throws Exception {
    var p = draft("snapshot noodle");
    String review = submit(p);
    call(
            "PATCH",
            "/posts/" + p.get("id").asText(),
            author,
            Map.of("version", 1, "content", content("mutated", List.of())))
        .andExpect(status().isConflict());
    call(
            "POST",
            "/admin/post-review-requests/" + review + "/reviews",
            author,
            Map.of("decision", "APPROVED", "reason", "self"))
        .andExpect(status().isForbidden());
    call("GET", "/post-review-requests/" + review, other, null).andExpect(status().isForbidden());
    call("GET", "/post-review-requests/" + review, author, null)
        .andExpect(jsonPath("$.snapshot.title").value("snapshot noodle"));
  }

  @Test
  void editingPublishedContentWithdrawsItUntilNewApproval() throws Exception {
    var p = publish();
    String id = p.get("id").asText();
    call(
            "PATCH",
            "/posts/" + id,
            author,
            Map.of(
                "version",
                p.get("version").asInt(),
                "content",
                content("edited noodle", List.of())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));
    call("GET", "/posts/" + id, 0, null).andExpect(status().isNotFound());
    call(
            "PATCH",
            "/posts/" + id,
            author,
            Map.of("version", 0, "content", content("stale", List.of())))
        .andExpect(status().isConflict());
  }

  @Test
  void hiddenAndDeletedPostsDisappearBeforeIndexDeliveryAndOldEventsCannotRestoreThem()
      throws Exception {
    var p = publish();
    deliver();
    assertThat(search("noodle").get("total").asLong()).isEqualTo(1);
    String id = p.get("id").asText();
    var event =
        jdbc.queryForMap(
            "SELECT id,payload FROM campus_outbox_event WHERE aggregate_id=? ORDER BY created_at"
                + " LIMIT 1",
            "SEARCH:POST:" + id);
    call(
            "PATCH",
            "/admin/posts/" + id,
            reviewer,
            Map.of("version", p.get("version").asInt(), "reason", "reported content"))
        .andExpect(status().isOk());
    assertThat(search("noodle").get("total").asLong()).isZero();
    String lateEvent = UUID.randomUUID().toString();
    manualEvents.add(lateEvent);
    projection.handle(
        new EventPublisherPort.Event(
            lateEvent,
            "SEARCH_RESOURCE_CHANGED",
            "SEARCH:POST:" + id,
            clock.instant(),
            Map.of("resourceType", "POST", "resourceId", id)));
    assertThat(
            jdbc.queryForObject(
                "SELECT visible FROM campus_search_document WHERE resource_type='POST' AND"
                    + " resource_id=?",
                Boolean.class,
                id))
        .isFalse();
    call("DELETE", "/posts/" + id, author, null).andExpect(status().isNoContent());
    deliver();
    assertThat(search("noodle").get("total").asLong()).isZero();
  }

  @Test
  void likesAreIdempotentAndAnonymousOrHiddenMutationsFail() throws Exception {
    var p = publish();
    String id = p.get("id").asText();
    call("PUT", "/posts/" + id + "/likes/me", other, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(1));
    call("PUT", "/posts/" + id + "/likes/me", other, null).andExpect(jsonPath("$.count").value(1));
    call("DELETE", "/posts/" + id + "/likes/me", other, null)
        .andExpect(jsonPath("$.count").value(0));
    call("DELETE", "/posts/" + id + "/likes/me", other, null)
        .andExpect(jsonPath("$.count").value(0));
    call("PUT", "/posts/" + id + "/likes/me", 0, null).andExpect(status().isUnauthorized());
  }

  @Test
  void twoConcurrentLikesCreateOnlyOneRelation() throws Exception {
    var p = publish();
    String id = p.get("id").asText(), bearer = token(other);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var latch = new CountDownLatch(1);
      var calls = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 2; i++)
        calls.add(
            pool.submit(
                () -> {
                  latch.await();
                  return mvc.perform(
                          put("/api/v1/posts/" + id + "/likes/me").header("Authorization", bearer))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      latch.countDown();
      for (var f : calls) assertThat(f.get(15, TimeUnit.SECONDS)).isEqualTo(200);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM campus_post_like WHERE post_id=?", Integer.class, id))
          .isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void commentsArePrivateUntilReviewedAndRetryKeysDoNotDuplicateThem() throws Exception {
    String id = publish().get("id").asText(), key = UUID.randomUUID().toString();
    var c = body(comment(id, other, key, "好吃 noodle").andExpect(status().isCreated()));
    comment(id, other, key, "好吃 noodle")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(c.get("id").asText()));
    comment(id, other, key, "changed").andExpect(status().isConflict());
    call("GET", "/posts/" + id + "/comments", 0, null).andExpect(jsonPath("$.total").value(0));
    call("GET", "/post-comments/" + c.get("id").asText(), cashier, null)
        .andExpect(status().isNotFound());
    call(
            "PATCH",
            "/admin/post-comments/" + c.get("id").asText(),
            reviewer,
            Map.of("version", 0, "status", "APPROVED", "reason", "approved"))
        .andExpect(status().isOk());
    call("GET", "/posts/" + id + "/comments", 0, null).andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void commentHidingAndAuthorDeletionCannotBeUndoneByOldModeration() throws Exception {
    String post = publish().get("id").asText();
    var c =
        body(
            comment(post, other, UUID.randomUUID().toString(), "review me")
                .andExpect(status().isCreated()));
    String id = c.get("id").asText();
    call(
            "PATCH",
            "/admin/post-comments/" + id,
            reviewer,
            Map.of("version", 0, "status", "APPROVED", "reason", "approved"))
        .andExpect(status().isOk());
    call(
            "PATCH",
            "/admin/post-comments/" + id,
            reviewer,
            Map.of("version", 1, "status", "HIDDEN", "reason", "hidden"))
        .andExpect(status().isOk());
    call("GET", "/posts/" + post + "/comments", 0, null).andExpect(jsonPath("$.total").value(0));
    call("DELETE", "/post-comments/" + id, other, null).andExpect(status().isNoContent());
    call(
            "PATCH",
            "/admin/post-comments/" + id,
            reviewer,
            Map.of("version", 3, "status", "APPROVED", "reason", "too late"))
        .andExpect(status().isConflict());
  }

  @Test
  void reportsAreUniqueAndPrivateAndResolutionIsAudited() throws Exception {
    String post = publish().get("id").asText();
    var r =
        body(
            call("POST", "/posts/" + post + "/reports", other, Map.of("reason", "不实信息"))
                .andExpect(status().isCreated()));
    call("POST", "/posts/" + post + "/reports", other, Map.of("reason", "再次举报"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(r.get("id").asText()));
    call("GET", "/post-reports/" + r.get("id").asText(), owner, null)
        .andExpect(status().isForbidden());
    call(
            "PATCH",
            "/admin/post-reports/" + r.get("id").asText(),
            reviewer,
            Map.of("version", 0, "status", "RESOLVED", "reason", "已核实并处理"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
  }

  @Test
  void communityImagesMustBeReadyOwnedAndHaveSeparatePurpose() throws Exception {
    String foreign = image(other, "COMMUNITY_IMAGE", "READY"),
        qualification = image(author, "MERCHANT_QUALIFICATION", "READY"),
        pending = image(author, "COMMUNITY_IMAGE", "PENDING");
    for (String image : List.of(foreign, qualification, pending)) {
      int status =
          call(
                  "POST",
                  "/posts",
                  author,
                  Map.of("schoolId", "" + school, "content", content("image test", List.of(image))))
              .andReturn()
              .getResponse()
              .getStatus();
      assertThat(status).isIn(404, 409);
    }
    String ready = image(author, "COMMUNITY_IMAGE", "READY");
    var p =
        body(
            call(
                    "POST",
                    "/posts",
                    author,
                    Map.of(
                        "schoolId", "" + school, "content", content("ready image", List.of(ready))))
                .andExpect(status().isCreated()));
    call("POST", "/posts/" + p.get("id").asText() + "/image-download-tickets", other, null)
        .andExpect(status().isNotFound());
    call("POST", "/posts/" + p.get("id").asText() + "/image-download-tickets", author, null)
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void localPolicyFiltersNormalizedTermsAndDoesNotPretendAutomaticReview() throws Exception {
    call(
            "POST",
            "/posts",
            author,
            Map.of(
                "schoolId",
                "" + school,
                "content",
                Map.of("title", "ＢＬＯＣＫＭＥ", "body", "hello", "imageIds", List.of())))
        .andExpect(status().isUnprocessableEntity());
    call("GET", "/community-capabilities", 0, null)
        .andExpect(jsonPath("$.mongoAvailable").value(false))
        .andExpect(jsonPath("$.automaticModerationAvailable").value(false));
  }

  @Test
  void moderationOutboxFailureRollsBackPublicationAndReviewDecision() throws Exception {
    var p = draft("rollback noodle");
    String request = submit(p);
    OutboxPort target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
    try {
      doThrow(new IllegalStateException("test projection append failure"))
          .when(target)
          .append(any());
      assertThatThrownBy(
              () ->
                  call(
                      "POST",
                      "/admin/post-review-requests/" + request + "/reviews",
                      reviewer,
                      Map.of("decision", "APPROVED", "reason", "test")))
          .hasRootCauseMessage("test projection append failure");
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM campus_post WHERE id=?", String.class, p.get("id").asText()))
          .isEqualTo("PENDING");
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM campus_post_review_request WHERE id=?",
                  String.class,
                  request))
          .isEqualTo("PENDING");
    } finally {
      reset(target);
    }
  }

  @Test
  void nearbyUsesDeclaredWgs84AndStableDistanceOrdering() throws Exception {
    call("GET", "/nearby-merchants?longitude=116.4&latitude=39.9&coordinateSystem=WGS84", 0, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    location(store, owner, "116.4000000", "39.9000000", 0, "WGS84");
    location(otherStore, otherOwner, "116.4100000", "39.9000000", 0, "WGS84");
    var results =
        body(
            call(
                    "GET",
                    "/nearby-merchants?longitude=116.4&latitude=39.9&coordinateSystem=WGS84&radiusMeters=2000",
                    0,
                    null)
                .andExpect(status().isOk()));
    assertThat(results.at("/items/0/id").asText()).isEqualTo("" + store);
    assertThat(results.at("/items/0/distanceMeters").asLong()).isZero();
    assertThat(results.at("/items/1/distanceMeters").asLong()).isBetween(800L, 900L);
  }

  @Test
  void wrongCoordinateSystemInvalidRangeAndStaleLocationAreRejected() throws Exception {
    call(
            "PUT",
            "/merchants/" + store + "/location",
            owner,
            Map.of("longitude", 116.4, "latitude", 39.9, "coordinateSystem", "GCJ02", "version", 0))
        .andExpect(status().isUnprocessableEntity());
    location(store, owner, "116.4", "39.9", 0, "WGS84");
    call(
            "PUT",
            "/merchants/" + store + "/location",
            owner,
            Map.of("longitude", 116.4, "latitude", 39.9, "coordinateSystem", "WGS84", "version", 0))
        .andExpect(status().isConflict());
    call("GET", "/nearby-merchants?longitude=181&latitude=39&coordinateSystem=WGS84", 0, null)
        .andExpect(status().isBadRequest());
    call(
            "GET",
            "/nearby-merchants?longitude=116&latitude=39&coordinateSystem=WGS84&radiusMeters=20001",
            0,
            null)
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void pausedMerchantsAndUnauthorisedLocationWritersAreExcluded() throws Exception {
    location(store, owner, "116.4", "39.9", 0, "WGS84");
    call(
            "PUT",
            "/merchants/" + store + "/location",
            cashier,
            Map.of("longitude", 116.4, "latitude", 39.9, "coordinateSystem", "WGS84", "version", 1))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE campus_merchant SET status='SUSPENDED' WHERE id=?", store);
    call("GET", "/nearby-merchants?longitude=116.4&latitude=39.9&coordinateSystem=WGS84", 0, null)
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void searchLimitsAndLiteralHighlightNeverAcceptArbitraryFieldsOrHtml() throws Exception {
    publish();
    deliver();
    mvc.perform(get("/api/v1/search-results").param("keyword", "noodle").param("page", "1000"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            get("/api/v1/search-results")
                .param("keyword", "noodle")
                .param("resourceType", "PRIVATE_USER"))
        .andExpect(status().isBadRequest());
    assertThat(search("%_").get("total").asLong()).isZero();
    var result = search("<script>");
    assertThat(result.at("/items/0/excerptSegments").isArray()).isTrue();
    assertThat(result.at("/items/0").has("highlightHtml")).isFalse();
  }

  @Test
  void rebuildCreatesProjectionAndIncludesTombstoneKeys() throws Exception {
    var p = publish();
    String id =
        body(call("POST", "/admin/search-index-rebuilds", reviewer, null)
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    call("POST", "/admin/search-index-rebuilds", reviewer, null).andExpect(status().isConflict());
    runRebuild(id);
    assertThat(search("noodle").get("total").asLong()).isGreaterThanOrEqualTo(3);
    call("DELETE", "/posts/" + p.get("id").asText(), author, null)
        .andExpect(status().isNoContent());
    String second =
        body(call("POST", "/admin/search-index-rebuilds", reviewer, null)
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    runRebuild(second);
    assertThat(
            jdbc.queryForObject(
                "SELECT visible FROM campus_search_document WHERE resource_type='POST' AND"
                    + " resource_id=?",
                Boolean.class,
                p.get("id").asText()))
        .isFalse();
  }

  private void runRebuild(String id) {
    for (int i = 0; i < 20 && !"COMPLETED".equals(search.rebuild(id, false).status()); i++)
      projection.rebuildOnce();
    assertThat(search.rebuild(id, false).status()).isEqualTo("COMPLETED");
  }

  @Test
  void elasticsearchCandidatesAreHydratedOnlyFromCurrentPublicRows() throws Exception {
    var post = publish();
    String id = post.get("id").asText();
    var source = search.source("POST", id);
    SearchEnginePort engine = mock(SearchEnginePort.class);
    when(engine.candidates(anyString(), any(), any(), anyInt()))
        .thenReturn(List.of(new SearchEnginePort.Key("POST", id, projection.hash(source))));
    var local =
        new com.campus.perks.search.serviceImpl.SearchServiceImpl(
            search,
            new com.campus.perks.search.service.SearchProperties(
                "ELASTICSEARCH", 1000, 240, 10, Duration.ofMinutes(2)),
            engine,
            projection,
            audit,
            accounts,
            clock);
    var query = new com.campus.perks.search.dto.SearchQuery();
    query.setKeyword("noodle");
    assertThat(local.query(query).items()).hasSize(1);
    query.setResourceType("MERCHANT");
    assertThat(local.query(query).items()).isEmpty();
    query.setResourceType(null);
    call(
            "PATCH",
            "/admin/posts/" + id,
            reviewer,
            Map.of("version", post.get("version").asInt(), "reason", "hidden"))
        .andExpect(status().isOk());
    assertThat(local.query(query).items()).isEmpty();
    jdbc.update(
        "UPDATE campus_post SET status='PUBLISHED',title='changed public content' WHERE id=?", id);
    assertThat(local.query(query).items()).isEmpty();
  }

  @Test
  void expiredRebuildLeaseRejectsOldWorkerAcknowledgement() throws Exception {
    publish();
    String id =
        body(call("POST", "/admin/search-index-rebuilds", reviewer, null)
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    var old = projection.lease();
    assertThat(old).isNotNull();
    assertThat(projection.lease()).isNull();
    clock.now = clock.now.plusSeconds(121);
    var current = projection.lease();
    assertThat(current.token()).isNotEqualTo(old.token());
    search.advance(old, null, 0, true, clock.instant());
    assertThat(search.rebuild(id, false).status()).isEqualTo("RUNNING");
    search.advance(current, null, 0, true, clock.instant());
    assertThat(search.rebuild(id, false).status()).isEqualTo("COMPLETED");
  }

  @AfterEach
  void cleanup() {
    for (String event : manualEvents)
      jdbc.update(
          "DELETE FROM campus_consumed_event WHERE consumer_name='campus-search' AND event_id=?",
          event);
    if (users.isEmpty()) return;
    String placeholders = String.join(",", Collections.nCopies(users.size(), "?"));
    Object[] args = users.toArray();
    var posts =
        jdbc.queryForList(
            "SELECT id FROM campus_post WHERE author_id IN (" + placeholders + ")",
            Long.class,
            args);
    var merchants =
        jdbc.queryForList(
            "SELECT id FROM campus_merchant WHERE owner_id IN (" + placeholders + ")",
            Long.class,
            args);
    var keys = new ArrayList<String>();
    for (long id : posts) keys.add("POST:" + id);
    for (long id : merchants) keys.add("MERCHANT:" + id);
    for (String job :
        jdbc.queryForList(
            "SELECT id FROM campus_search_rebuild WHERE requested_by IN (" + placeholders + ")",
            String.class,
            args)) {
      for (String key : keys) {
        String event =
            UUID.nameUUIDFromBytes(
                    (job + ":" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        jdbc.update(
            "DELETE FROM campus_consumed_event WHERE consumer_name='campus-search' AND event_id=?",
            event);
      }
      jdbc.update("DELETE FROM campus_search_rebuild WHERE id=?", job);
    }
    SearchFixtureCleanup.resources(jdbc, "POST", posts);
    SearchFixtureCleanup.resources(jdbc, "MERCHANT", merchants);
    for (long post : posts) {
      for (String table :
          List.of(
              "campus_post_like",
              "campus_post_comment",
              "campus_post_report",
              "campus_post_review_request",
              "campus_post_image")) jdbc.update("DELETE FROM " + table + " WHERE post_id=?", post);
      jdbc.update("DELETE FROM campus_post WHERE id=?", post);
    }
    jdbc.update(
        "DELETE FROM campus_community_moderation WHERE actor_id IN (" + placeholders + ")", args);
    jdbc.update("DELETE FROM campus_audit_event WHERE actor_id IN (" + placeholders + ")", args);
    for (long merchant : merchants) {
      jdbc.update("DELETE FROM campus_merchant_location WHERE merchant_id=?", merchant);
      jdbc.update("DELETE FROM campus_merchant_member WHERE merchant_id=?", merchant);
      jdbc.update("DELETE FROM campus_merchant WHERE id=?", merchant);
    }
    for (long user : users) {
      jdbc.update("DELETE FROM campus_file_object WHERE owner_id=?", user);
      jdbc.update("DELETE FROM campus_auth_session WHERE user_id=?", user);
      jdbc.update("DELETE FROM campus_user_role WHERE user_id=?", user);
    }
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
    for (long user : users) jdbc.update("DELETE FROM campus_user WHERE id=?", user);
    jdbc.update("DELETE FROM campus_school WHERE id=?", school);
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock communityClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant now = Instant.now();

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}
