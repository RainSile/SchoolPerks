package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.campus.perks.user.service.AuthSecrets;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.security.SecureRandom;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
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

@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.files.max-bytes=256",
      "campus.files.max-pending-per-user=2",
      "campus.merchant.max-members=2"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
@Transactional
@Import(MerchantStageTwoMySqlTest.TimeConfiguration.class)
class MerchantStageTwoMySqlTest {
  private static final AtomicLong PHONES = new AtomicLong(30000000);

  private static String key() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
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
  @Autowired MerchantAccessPort publication;
  @MockitoBean ObjectStoragePort storage;
  @MockitoSpyBean AuditPort audit;
  private long school, ownerA, ownerB, reviewer, staff, campusReviewer;
  private final List<Long> users = new ArrayList<>(),
      roles = new ArrayList<>(),
      createdPermissions = new ArrayList<>();
  private final AtomicReference<ObjectStoragePort.StoredObject> snapshot = new AtomicReference<>();
  private final AtomicInteger seals = new AtomicInteger();
  private final List<String> sealedTargets = new ArrayList<>();

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
            "stage2-test-" + UUID.randomUUID());
    users.add(id);
    return id;
  }

  private void grant(long user, String permission) {
    Long pid =
        jdbc.query(
            "SELECT id FROM campus_permission WHERE code=?",
            rs -> rs.next() ? rs.getLong(1) : null,
            permission);
    if (pid == null) {
      pid =
          insert("INSERT INTO campus_permission(code,name) VALUES(?,?)", permission, "stage2-test");
      createdPermissions.add(pid);
    }
    long role =
        insert(
            "INSERT INTO campus_role(code,name) VALUES(?,?)",
            UUID.randomUUID().toString(),
            "stage2-test");
    roles.add(role);
    jdbc.update("INSERT INTO campus_role_permission(role_id,permission_id) VALUES(?,?)", role, pid);
    jdbc.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", user, role);
  }

  @BeforeEach
  void fixtures() {
    clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    school =
        insert("INSERT INTO campus_school(name) VALUES(?)", "stage2-test-" + UUID.randomUUID());
    ownerA = user();
    ownerB = user();
    reviewer = user();
    staff = user();
    campusReviewer = user();
    jdbc.update(
        "UPDATE campus_user SET account_type='ADMIN' WHERE id IN (?,?)", reviewer, campusReviewer);
    grant(reviewer, "merchant:review");
    grant(campusReviewer, "campus:review");
    snapshot.set(
        new ObjectStoragePort.StoredObject(
            64,
            "application/pdf",
            "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    when(storage.available()).thenReturn(true);
    when(storage.createUploadUrl(anyString(), anyString(), any()))
        .thenReturn(URI.create("https://storage.invalid/pending?signature=test"));
    when(storage.createDownloadUrl(anyString(), any()))
        .thenReturn(URI.create("https://storage.invalid/private?signature=test"));
    when(storage.seal(anyString(), anyString()))
        .thenAnswer(
            i -> {
              seals.incrementAndGet();
              sealedTargets.add(i.getArgument(1));
              return snapshot.get();
            });
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
        java.sql.Timestamp.from(now.plus(Duration.ofDays(1))),
        user);
    return "Bearer "
        + issuer.issue(Long.toString(user), sid, List.of(), now, now.plus(Duration.ofMinutes(15)));
  }

  private ResultActions post(String path, long actor, Object body) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1" + path)
            .header("Authorization", token(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private ResultActions get(String path, long actor) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1" + path)
            .header("Authorization", token(actor)));
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private JsonNode upload(long actor) throws Exception {
    return body(
        post(
                "/uploads",
                actor,
                Map.of(
                    "fileName",
                    "license.pdf",
                    "contentType",
                    "application/pdf",
                    "size",
                    64,
                    "purpose",
                    "MERCHANT_QUALIFICATION"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location")));
  }

  private String ready(long actor) throws Exception {
    String id = upload(actor).get("id").asText();
    complete(id, actor).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
    return id;
  }

  private ResultActions complete(String id, long actor) throws Exception {
    return mvc.perform(
        put("/api/v1/uploads/" + id + "/completion").header("Authorization", token(actor)));
  }

  private Map<String, Object> application(String file) {
    return Map.of(
        "schoolId",
        Long.toString(school),
        "name",
        "stage2-test-store",
        "category",
        "DINING",
        "address",
        "stage2-test-address",
        "description",
        "test description",
        "qualificationFileId",
        file);
  }

  private String apply(long actor) throws Exception {
    return body(post("/merchant-applications", actor, application(ready(actor)))
            .andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  private String approve(String application) throws Exception {
    post(
            "/admin/merchant-applications/" + application + "/reviews",
            reviewer,
            Map.of("decision", "APPROVED", "reason", "stage2 test approval"))
        .andExpect(status().isCreated());
    return body(get("/admin/merchant-applications/" + application, reviewer))
        .get("merchantId")
        .asText();
  }

  @Test
  void storageUnavailableNeverCreatesFakeTicketsOrMetadata() throws Exception {
    when(storage.available()).thenReturn(false);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/storage-capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false));
    post(
            "/uploads",
            ownerA,
            Map.of(
                "fileName",
                "license.pdf",
                "contentType",
                "application/pdf",
                "size",
                64,
                "purpose",
                "MERCHANT_QUALIFICATION"))
        .andExpect(status().isServiceUnavailable());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_file_object WHERE owner_id=?", Integer.class, ownerA))
        .isZero();
  }

  @Test
  void uploadValidatesOwnershipAndSealsOnceWithoutExposingKeys() throws Exception {
    var ticket = upload(ownerA);
    String id = ticket.get("id").asText();
    get("/uploads/" + id, ownerB).andExpect(status().isNotFound());
    complete(id, ownerB).andExpect(status().isNotFound());
    post("/merchant-applications", ownerA, application(id)).andExpect(status().isConflict());
    var metadata = body(complete(id, ownerA).andExpect(status().isOk()));
    assertThat(metadata.toString())
        .doesNotContain("stagingKey", "immutableKey", "storage.invalid", "ownerId");
    complete(id, ownerA).andExpect(status().isOk());
    assertThat(seals.get()).isEqualTo(1);
    String source =
        jdbc.queryForObject(
            "SELECT staging_key FROM campus_file_object WHERE id=?", String.class, id);
    assertThat(sealedTargets.get(0)).isNotEqualTo(source).contains("/sealed/");
    post("/uploads/" + id + "/download-tickets", ownerB, Map.of()).andExpect(status().isNotFound());
    post("/uploads/" + id + "/download-tickets", reviewer, Map.of())
        .andExpect(status().isNotFound());
    post("/uploads/" + id + "/download-tickets", ownerA, Map.of())
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    verify(storage).createDownloadUrl(eq(sealedTargets.get(0)), eq(Duration.ofMinutes(2)));
  }

  @Test
  void actualSizeMimeSignatureExpiryLimitsAndUntrustedUrlsAreChecked() throws Exception {
    post(
            "/uploads",
            ownerA,
            Map.of(
                "fileName",
                "bad.html",
                "contentType",
                "text/html",
                "size",
                64,
                "purpose",
                "MERCHANT_QUALIFICATION"))
        .andExpect(status().isBadRequest());
    post(
            "/uploads",
            ownerA,
            Map.of(
                "fileName",
                "big.pdf",
                "contentType",
                "application/pdf",
                "size",
                257,
                "purpose",
                "MERCHANT_QUALIFICATION"))
        .andExpect(status().isBadRequest());
    post(
            "/uploads",
            ownerA,
            Map.of(
                "fileName",
                "../license.pdf",
                "contentType",
                "application/pdf",
                "size",
                64,
                "purpose",
                "MERCHANT_QUALIFICATION"))
        .andExpect(status().isBadRequest());
    String id = upload(ownerA).get("id").asText();
    snapshot.set(new ObjectStoragePort.StoredObject(63, "application/pdf", "%PDF-".getBytes()));
    complete(id, ownerA).andExpect(status().isUnprocessableEntity());
    snapshot.set(new ObjectStoragePort.StoredObject(64, "image/png", new byte[] {0}));
    complete(id, ownerA).andExpect(status().isUnprocessableEntity());
    snapshot.set(new ObjectStoragePort.StoredObject(64, "application/pdf", "<html>".getBytes()));
    complete(id, ownerA).andExpect(status().isUnprocessableEntity());
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM campus_file_object WHERE id=?", String.class, id))
        .isEqualTo("PENDING");
    upload(ownerA);
    post(
            "/uploads",
            ownerA,
            Map.of(
                "fileName",
                "third.pdf",
                "contentType",
                "application/pdf",
                "size",
                64,
                "purpose",
                "MERCHANT_QUALIFICATION"))
        .andExpect(status().isTooManyRequests());
    clock.advance(Duration.ofMinutes(6));
    complete(id, ownerA).andExpect(status().isGone());
    when(storage.createUploadUrl(anyString(), anyString(), any()))
        .thenReturn(URI.create("javascript:alert(1)"));
    post(
            "/uploads",
            ownerA,
            Map.of(
                "fileName",
                "invalid.pdf",
                "contentType",
                "application/pdf",
                "size",
                64,
                "purpose",
                "MERCHANT_QUALIFICATION"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void applicationRequiresOwnReadyQualificationAndDoesNotExposePendingMerchants() throws Exception {
    String file = ready(ownerA);
    post("/merchant-applications", ownerB, application(file)).andExpect(status().isNotFound());
    var request = new HashMap<>(application(file));
    request.put("longitude", 1);
    post("/merchant-applications", ownerA, request).andExpect(status().isBadRequest());
    var a =
        body(
            post("/merchant-applications", ownerA, application(file))
                .andExpect(status().isCreated()));
    String id = a.get("id").asText();
    post("/merchant-applications", ownerA, application(file)).andExpect(status().isConflict());
    get("/merchant-applications/" + id, ownerB).andExpect(status().isNotFound());
    get("/admin/merchant-applications", ownerA).andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_merchant WHERE owner_id=?", Integer.class, ownerA))
        .isZero();
    get("/admin/merchant-applications/" + id, reviewer).andExpect(status().isOk());
    post(
            "/admin/merchant-applications/" + id + "/qualification-download-tickets",
            reviewer,
            Map.of())
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_audit_event WHERE actor_id=? AND resource_id=? AND"
                    + " action='PRIVATE_FILE_DOWNLOAD_ISSUED'",
                Integer.class,
                reviewer,
                id))
        .isEqualTo(1);
  }

  @Test
  void approvalCreatesOneStoreAndLiveMembershipWithoutPlatformRoleEscalation() throws Exception {
    String id = apply(ownerA), oldToken = token(ownerA);
    var review =
        body(
            post(
                    "/admin/merchant-applications/" + id + "/reviews",
                    reviewer,
                    Map.of("decision", "APPROVED", "reason", "approved after qualification review"))
                .andExpect(status().isCreated()));
    String merchant =
        body(get("/admin/merchant-applications/" + id, reviewer)).get("merchantId").asText();
    get("/admin/merchant-reviews/" + review.get("id").asText(), reviewer)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewerId").value(Long.toString(reviewer)));
    post(
            "/admin/merchant-applications/" + id + "/reviews",
            reviewer,
            Map.of("decision", "REJECTED", "reason", "cannot overwrite"))
        .andExpect(status().isConflict());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/users/me")
                .header("Authorization", oldToken))
        .andExpect(status().isUnauthorized());
    get("/users/me", ownerA)
        .andExpect(jsonPath("$.accountType").value("MERCHANT"))
        .andExpect(jsonPath("$.homePath").value("/merchant"));
    get("/merchants/" + merchant + "/workspace", ownerA)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("OWNER"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/merchants/" + merchant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.qualificationFileId").doesNotExist())
        .andExpect(jsonPath("$.ownerId").doesNotExist());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_user_role WHERE user_id=?", Integer.class, ownerA))
        .isZero();
  }

  @Test
  void rejectedApplicationsRetainHistoryAndAllowResubmissionButSelfReviewIsForbidden()
      throws Exception {
    String id = apply(ownerA);
    grant(ownerA, "merchant:review");
    post(
            "/admin/merchant-applications/" + id + "/reviews",
            ownerA,
            Map.of("decision", "APPROVED", "reason", "self review"))
        .andExpect(status().isForbidden());
    post(
            "/admin/merchant-applications/" + id + "/reviews",
            reviewer,
            Map.of("decision", "REJECTED", "reason", "qualification needs correction"))
        .andExpect(status().isCreated());
    get("/merchant-applications/" + id, ownerA)
        .andExpect(jsonPath("$.reviewReason").value("qualification needs correction"));
    post("/merchant-applications", ownerA, application(ready(ownerA)))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_merchant WHERE owner_id=?", Integer.class, ownerA))
        .isZero();
  }

  @Test
  void merchantsCannotReadOrMutateOtherStoresAndCashiersCannotManageQualifications()
      throws Exception {
    String merchant = approve(apply(ownerA));
    jdbc.update("UPDATE campus_user SET account_type='MERCHANT' WHERE id=?", ownerB);
    get("/merchants/" + merchant + "/workspace", ownerB).andExpect(status().isNotFound());
    get("/merchants/" + merchant + "/members", ownerB).andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/merchants/" + merchant)
                .header("Authorization", token(ownerB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"description\":\"x\",\"businessHours\":\"x\"}"))
        .andExpect(status().isNotFound());
    post("/merchants/" + merchant + "/qualification-download-tickets", ownerB, Map.of())
        .andExpect(status().isNotFound());
    post(
            "/merchants/" + merchant + "/members",
            ownerA,
            Map.of("userId", Long.toString(staff), "role", "CASHIER"))
        .andExpect(status().isCreated());
    get("/merchants/" + merchant + "/workspace", staff).andExpect(status().isOk());
    get("/merchants/" + merchant + "/members", staff).andExpect(status().isForbidden());
    post("/merchants/" + merchant + "/qualification-download-tickets", staff, Map.of())
        .andExpect(status().isForbidden());
    post(
            "/merchants/" + merchant + "/members",
            ownerA,
            Map.of("userId", Long.toString(ownerB), "role", "MANAGER"))
        .andExpect(status().isConflict());
    post(
            "/merchants/" + merchant + "/members",
            ownerA,
            Map.of("userId", Long.toString(ownerB), "role", "OWNER"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            delete("/api/v1/merchants/" + merchant + "/members/" + ownerA)
                .header("Authorization", token(ownerA)))
        .andExpect(status().isConflict());
    String old = token(staff);
    mvc.perform(
            delete("/api/v1/merchants/" + merchant + "/members/" + staff)
                .header("Authorization", token(ownerA)))
        .andExpect(status().isNoContent());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/merchants/" + merchant + "/workspace")
                .header("Authorization", old))
        .andExpect(status().isNotFound());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/users/me")
                .header("Authorization", old))
        .andExpect(
            jsonPath("$.permissions")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("merchant:read"))));
  }

  @Test
  void updatesUseWhitelistAndVersionAndSuspendedStoresAreHiddenAndCannotPublish() throws Exception {
    String merchant = approve(apply(ownerA));
    String ownerToken = token(ownerA);
    mvc.perform(
            patch("/api/v1/merchants/" + merchant)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"version\":0,\"description\":\"new"
                        + " description\",\"businessHours\":\"10:00-22:00\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            patch("/api/v1/merchants/" + merchant)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"description\":\"stale\",\"businessHours\":\"x\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            patch("/api/v1/merchants/" + merchant)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"version\":1,\"description\":\"x\",\"businessHours\":\"x\",\"status\":\"APPROVED\"}"))
        .andExpect(status().isBadRequest());
    var change =
        body(
            post(
                    "/admin/merchants/" + merchant + "/status-changes",
                    reviewer,
                    Map.of("status", "SUSPENDED", "version", 1, "reason", "test suspension"))
                .andExpect(status().isCreated()));
    get("/admin/merchants/" + merchant + "/status-changes/" + change.get("id").asText(), reviewer)
        .andExpect(status().isOk());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/merchants/" + merchant))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/merchants/" + merchant)
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"description\":\"x\",\"businessHours\":\"x\"}"))
        .andExpect(status().isConflict());
    var auth =
        new org.springframework.security.oauth2.server.resource.authentication
            .JwtAuthenticationToken(
            org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(Long.toString(ownerA))
                .build(),
            List.of());
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(auth);
    try {
      assertThatThrownBy(() -> publication.requirePromotionPublisher(merchant))
          .isInstanceOf(com.campus.perks.common.exception.ApplicationException.class)
          .hasMessageContaining("暂停");
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    post(
            "/admin/merchants/" + merchant + "/status-changes",
            reviewer,
            Map.of("status", "APPROVED", "version", 2, "reason", "test restoration"))
        .andExpect(status().isCreated());
  }

  @Test
  void campusReviewRequiresSeparatePermissionAuditsDecryptionAndUpdatesVerifiedSchool()
      throws Exception {
    var application =
        body(
            post(
                    "/users/me/campus-verifications",
                    ownerA,
                    Map.of(
                        "schoolId",
                        Long.toString(school),
                        "realName",
                        "stage2-test-name",
                        "studentNumber",
                        "STAGE22026"))
                .andExpect(status().isCreated()));
    String id = application.get("id").asText();
    get("/admin/campus-verifications", reviewer).andExpect(status().isForbidden());
    get("/admin/campus-verifications/" + id + "/identity", ownerB)
        .andExpect(status().isForbidden());
    get("/admin/campus-verifications/" + id + "/identity", campusReviewer)
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.studentNumber").value("STAGE22026"));
    assertThat(
            jdbc.queryForObject(
                "SELECT details FROM campus_audit_event WHERE action='CAMPUS_IDENTITY_VIEWED' AND"
                    + " resource_id=?",
                String.class,
                id))
        .doesNotContain("STAGE22026", "stage2-test-name");
    post(
            "/admin/campus-verifications/" + id + "/reviews",
            campusReviewer,
            Map.of("decision", "APPROVED", "reason", "identity matched"))
        .andExpect(status().isCreated());
    get("/users/me", ownerA)
        .andExpect(jsonPath("$.schoolId").value(Long.toString(school)))
        .andExpect(jsonPath("$.campusVerificationStatus").value("APPROVED"));
    post(
            "/admin/campus-verifications/" + id + "/reviews",
            campusReviewer,
            Map.of("decision", "REJECTED", "reason", "cannot overwrite"))
        .andExpect(status().isConflict());
  }

  @Test
  void auditQueryRequiresIndependentPermissionAndOmitsPrivateContents() throws Exception {
    String application = apply(ownerA);
    get("/admin/audit-events", reviewer).andExpect(status().isForbidden());
    grant(staff, "audit:read");
    jdbc.update("UPDATE campus_user SET account_type='ADMIN' WHERE id=?", staff);
    var events =
        body(
            get("/admin/audit-events?resourceId=" + application, staff).andExpect(status().isOk()));
    assertThat(events.get("total").asInt()).isEqualTo(1);
    String event = events.get("items").get(0).get("id").asText();
    get("/admin/audit-events/" + event, staff)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("MERCHANT_APPLICATION_SUBMITTED"))
        .andExpect(jsonPath("$.details").isEmpty());
    get("/admin/merchant-applications", staff).andExpect(status().isForbidden());
  }

  @Test
  void publicationGuardAllowsOwnersAndManagersButRejectsCashiers() throws Exception {
    String merchant = approve(apply(ownerA));
    var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
    try {
      context.setAuthentication(actor(ownerA));
      publication.requirePromotionPublisher(merchant);
      post(
              "/merchants/" + merchant + "/members",
              ownerA,
              Map.of("userId", Long.toString(staff), "role", "MANAGER"))
          .andExpect(status().isCreated());
      get("/merchants/" + merchant + "/members/" + staff, ownerA)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value("MANAGER"));
      // MockMvc clears the context after each request; set it explicitly for the internal port.
      context = org.springframework.security.core.context.SecurityContextHolder.getContext();
      context.setAuthentication(actor(staff));
      publication.requirePromotionPublisher(merchant);
      mvc.perform(
              delete("/api/v1/merchants/" + merchant + "/members/" + staff)
                  .header("Authorization", token(ownerA)))
          .andExpect(status().isNoContent());
      post(
              "/merchants/" + merchant + "/members",
              ownerA,
              Map.of("userId", Long.toString(staff), "role", "CASHIER"))
          .andExpect(status().isCreated());
      org.springframework.security.core.context.SecurityContextHolder.getContext()
          .setAuthentication(actor(staff));
      assertThatThrownBy(() -> publication.requirePromotionPublisher(merchant))
          .isInstanceOf(com.campus.perks.common.exception.ApplicationException.class)
          .hasMessageContaining("无权");
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  private org.springframework.security.core.Authentication actor(long id) {
    return new org.springframework.security.oauth2.server.resource.authentication
        .JwtAuthenticationToken(
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject(Long.toString(id))
            .build(),
        List.of());
  }

  @Test
  void disabledApplicantsCannotBeApprovedButCanBeRejectedAndCampusSelfReviewIsForbidden()
      throws Exception {
    String application = apply(ownerA);
    var campus =
        body(post(
                    "/users/me/campus-verifications",
                    ownerA,
                    Map.of(
                        "schoolId",
                        Long.toString(school),
                        "realName",
                        "test",
                        "studentNumber",
                        "TEST2026"))
                .andExpect(status().isCreated()))
            .get("id")
            .asText();
    grant(ownerA, "campus:review");
    post(
            "/admin/campus-verifications/" + campus + "/reviews",
            ownerA,
            Map.of("decision", "APPROVED", "reason", "self"))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE campus_user SET status='DISABLED' WHERE id=?", ownerA);
    post(
            "/admin/merchant-applications/" + application + "/reviews",
            reviewer,
            Map.of("decision", "APPROVED", "reason", "disabled"))
        .andExpect(status().isConflict());
    post(
            "/admin/merchant-applications/" + application + "/reviews",
            reviewer,
            Map.of("decision", "REJECTED", "reason", "disabled"))
        .andExpect(status().isCreated());
    post(
            "/admin/campus-verifications/" + campus + "/reviews",
            campusReviewer,
            Map.of("decision", "APPROVED", "reason", "disabled"))
        .andExpect(status().isConflict());
    var record =
        body(
            post(
                    "/admin/campus-verifications/" + campus + "/reviews",
                    campusReviewer,
                    Map.of("decision", "REJECTED", "reason", "disabled"))
                .andExpect(status().isCreated()));
    get("/admin/campus-verification-reviews/" + record.get("id").asText(), campusReviewer)
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject("SELECT school_id FROM campus_user WHERE id=?", Long.class, ownerA))
        .isNull();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentReviewCreatesOnlyOneStoreOwnerAndHistoryRecord() throws Exception {
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      String id = apply(ownerA), reviewToken = token(reviewer);
      String input =
          json.writeValueAsString(Map.of("decision", "APPROVED", "reason", "concurrent review"));
      var start = new CountDownLatch(1);
      Callable<MvcResult> task =
          () -> {
            start.await();
            return mvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                            "/api/v1/admin/merchant-applications/" + id + "/reviews")
                        .header("Authorization", reviewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(input))
                .andReturn();
          };
      var first = workers.submit(task);
      var second = workers.submit(task);
      start.countDown();
      var a = first.get(15, TimeUnit.SECONDS);
      var b = second.get(15, TimeUnit.SECONDS);
      assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
          .containsExactlyInAnyOrder(201, 409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM campus_merchant WHERE owner_id=?", Integer.class, ownerA))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM campus_merchant_review WHERE application_id=?",
                  Integer.class,
                  id))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM campus_audit_event WHERE resource_id=? AND"
                      + " action='MERCHANT_APPLICATION_REVIEWED'",
                  Integer.class,
                  id))
          .isEqualTo(1);
    } finally {
      workers.shutdownNow();
      workers.awaitTermination(15, TimeUnit.SECONDS);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void auditFailureRollsBackApprovalStoreAndOwnerRelation() throws Exception {
    String id = apply(ownerA);
    AuditPort target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
    doThrow(new IllegalStateException("test-audit-failure"))
        .when(target)
        .record(anyString(), eq("MERCHANT_APPLICATION_REVIEWED"), anyString(), eq(id), anyMap());
    assertThatThrownBy(
            () ->
                post(
                    "/admin/merchant-applications/" + id + "/reviews",
                    reviewer,
                    Map.of("decision", "APPROVED", "reason", "must be atomic")))
        .isInstanceOf(jakarta.servlet.ServletException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_merchant WHERE owner_id=?", Integer.class, ownerA))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM campus_merchant_application WHERE id=?", String.class, id))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_merchant_review WHERE application_id=?",
                Integer.class,
                id))
        .isZero();
  }

  @AfterEach
  void cleanupCommittedFixtures() {
    if (TestTransaction.isActive() || users.isEmpty()) return;
    String ids = users.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    Object[] values = users.toArray();
    SearchFixtureCleanup.resources(
        jdbc,
        "MERCHANT",
        jdbc.queryForList(
            "SELECT id FROM campus_merchant WHERE owner_id IN (" + ids + ")", Long.class, values));
    jdbc.update("DELETE FROM campus_audit_event WHERE actor_id IN (" + ids + ")", values);
    jdbc.update(
        "DELETE FROM campus_campus_verification_review WHERE verification_id IN (SELECT id FROM"
            + " campus_campus_verification WHERE user_id IN ("
            + ids
            + "))",
        values);
    jdbc.update("DELETE FROM campus_campus_verification WHERE user_id IN (" + ids + ")", values);
    jdbc.update(
        "DELETE FROM campus_merchant_review WHERE application_id IN (SELECT id FROM"
            + " campus_merchant_application WHERE applicant_id IN ("
            + ids
            + "))",
        values);
    jdbc.update(
        "DELETE FROM campus_merchant_application WHERE applicant_id IN (" + ids + ")", values);
    jdbc.update(
        "DELETE FROM campus_merchant_status_change WHERE merchant_id IN (SELECT id FROM"
            + " campus_merchant WHERE owner_id IN ("
            + ids
            + "))",
        values);
    jdbc.update(
        "DELETE FROM campus_merchant_member WHERE merchant_id IN (SELECT id FROM campus_merchant"
            + " WHERE owner_id IN ("
            + ids
            + "))",
        values);
    jdbc.update("DELETE FROM campus_merchant WHERE owner_id IN (" + ids + ")", values);
    jdbc.update("DELETE FROM campus_file_object WHERE owner_id IN (" + ids + ")", values);
    jdbc.update(
        "DELETE FROM campus_refresh_token WHERE session_id IN (SELECT id FROM campus_auth_session"
            + " WHERE user_id IN ("
            + ids
            + "))",
        values);
    jdbc.update("DELETE FROM campus_auth_session WHERE user_id IN (" + ids + ")", values);
    jdbc.update("DELETE FROM campus_user_role WHERE user_id IN (" + ids + ")", values);
    for (long role : roles) {
      jdbc.update("DELETE FROM campus_role_permission WHERE role_id=?", role);
      jdbc.update("DELETE FROM campus_role WHERE id=?", role);
    }
    for (long permission : createdPermissions)
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
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    Instant now = Instant.now();

    void advance(Duration d) {
      now = now.plus(d);
    }

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
