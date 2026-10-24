package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.SmsSenderPort;
import com.campus.perks.user.service.AuthSecrets;
import com.fasterxml.jackson.databind.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uses only the explicitly authorized database; every fixture rolls back. SMS Mock exists only
 * here.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.auth.max-code-attempts=3",
      "campus.auth.phone-window-limit=2",
      "campus.auth.ip-window-limit=3"
    })
@AutoConfigureMockMvc(
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("local")
@Transactional
@Import(AuthenticationMySqlTest.TimeConfiguration.class)
class AuthenticationMySqlTest {
  private static final AtomicLong PHONES = new AtomicLong(10000000);

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private static final String JWT_KEY = randomKey(),
      OTP_KEY = randomKey(),
      IDENTITY_KEY = randomKey();

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) {
    r.add("campus.security.jwt.secret-base64", () -> JWT_KEY);
    r.add("campus.auth.code-hash-secret-base64", () -> OTP_KEY);
    r.add("campus.auth.identity-key-base64", () -> IDENTITY_KEY);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired MutableClock clock;
  @Autowired AuthSecrets secrets;
  @MockitoBean SmsSenderPort sms;
  private final Map<String, String> codes = new HashMap<>();

  @BeforeEach
  void prepare() {
    clock.value = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    when(sms.available()).thenReturn(true);
    doAnswer(
            invocation -> {
              codes.put(invocation.getArgument(0), invocation.getArgument(2));
              return null;
            })
        .when(sms)
        .sendVerificationCode(anyString(), anyString(), anyString(), any(Duration.class));
  }

  private String phone() {
    String candidate;
    do {
      candidate = "139" + PHONES.incrementAndGet();
    } while (jdbc.queryForObject(
            "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, candidate)
        > 0);
    return candidate;
  }

  private ResultActions postJson(String path, Object body) throws Exception {
    return mvc.perform(
        post("/api/v1" + path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private JsonNode challenge(String phone) throws Exception {
    return body(
        postJson("/verification-challenges", Map.of("phone", phone, "purpose", "LOGIN"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.phone").doesNotExist()));
  }

  private JsonNode login(String phone) throws Exception {
    var c = challenge(phone);
    return body(
        postJson(
                "/sessions",
                Map.of(
                    "phone", phone, "challengeId", c.get("id").asText(), "code", codes.get(phone)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store")));
  }

  private String access(JsonNode tokens) {
    return "Bearer " + tokens.get("accessToken").asText();
  }

  private String user(JsonNode tokens) throws Exception {
    return body(mvc.perform(get("/api/v1/users/me").header("Authorization", access(tokens)))
            .andExpect(status().isOk()))
        .get("id")
        .asText();
  }

  @Test
  void loginRegistersOnceConsumesCodeAndNeverLeaksCredentialsInProfile() throws Exception {
    String phone = phone();
    var c = challenge(phone);
    String id = c.get("id").asText(), code = codes.get(phone);
    assertThat(
            jdbc.queryForObject(
                "SELECT code_hash FROM campus_verification_challenge WHERE id=?", String.class, id))
        .hasSize(64)
        .isNotEqualTo(code);
    var input = Map.of("phone", phone, "challengeId", id, "code", code);
    var tokens = body(postJson("/sessions", input).andExpect(status().isCreated()));
    var profile =
        body(
            mvc.perform(get("/api/v1/users/me").header("Authorization", access(tokens)))
                .andExpect(status().isOk()));
    assertThat(profile.get("maskedPhone").asText())
        .isEqualTo(phone.substring(0, 3) + "****" + phone.substring(7));
    assertThat(profile.toString())
        .doesNotContain(phone, "accessToken", "refreshToken", "passwordHash", "codeHash");
    postJson("/sessions", input)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, phone))
        .isEqualTo(1);
  }

  @Test
  void expiredWrongAndExhaustedCodesCannotBeUsed() throws Exception {
    String phone = phone();
    var c = challenge(phone);
    String correct = codes.get(phone), wrong = correct.equals("000000") ? "999999" : "000000";
    for (int i = 0; i < 3; i++)
      postJson(
              "/sessions",
              Map.of("phone", phone, "challengeId", c.get("id").asText(), "code", wrong))
          .andExpect(status().isUnauthorized());
    postJson(
            "/sessions",
            Map.of("phone", phone, "challengeId", c.get("id").asText(), "code", correct))
        .andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT attempts FROM campus_verification_challenge WHERE id=?",
                Integer.class,
                c.get("id").asText()))
        .isEqualTo(3);
    String other = phone();
    var expired = challenge(other);
    clock.advance(Duration.ofMinutes(6));
    postJson(
            "/sessions",
            Map.of(
                "phone",
                other,
                "challengeId",
                expired.get("id").asText(),
                "code",
                codes.get(other)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/verification-challenges/" + expired.get("id").asText()))
        .andExpect(jsonPath("$.status").value("EXPIRED"));
  }

  @Test
  void resendInvalidatesOldChallengeAndPhoneRateLimitSurvivesDenials() throws Exception {
    String phone = phone();
    var first = challenge(phone);
    String old = codes.get(phone);
    postJson("/verification-challenges", Map.of("phone", phone, "purpose", "LOGIN"))
        .andExpect(status().isTooManyRequests());
    clock.advance(Duration.ofSeconds(61));
    var second = challenge(phone);
    postJson(
            "/sessions",
            Map.of("phone", phone, "challengeId", first.get("id").asText(), "code", old))
        .andExpect(status().isUnauthorized());
    clock.advance(Duration.ofSeconds(61));
    postJson("/verification-challenges", Map.of("phone", phone, "purpose", "LOGIN"))
        .andExpect(status().isTooManyRequests());
    postJson(
            "/sessions",
            Map.of(
                "phone", phone, "challengeId", second.get("id").asText(), "code", codes.get(phone)))
        .andExpect(status().isCreated());
  }

  @Test
  void ipLimitCannotBeBypassedWithOtherPhones() throws Exception {
    for (int i = 0; i < 3; i++) challenge(phone());
    postJson("/verification-challenges", Map.of("phone", phone(), "purpose", "LOGIN"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void refreshRotatesAndReplayRevokesTheWholeFamily() throws Exception {
    var initial = login(phone());
    String old = initial.get("refreshToken").asText();
    var rotated =
        body(
            postJson("/session-refreshes", Map.of("refreshToken", old))
                .andExpect(status().isCreated()));
    assertThat(rotated.get("refreshToken").asText()).isNotEqualTo(old);
    mvc.perform(get("/api/v1/users/me").header("Authorization", access(rotated)))
        .andExpect(status().isOk());
    postJson("/session-refreshes", Map.of("refreshToken", old))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/users/me").header("Authorization", access(rotated)))
        .andExpect(status().isUnauthorized());
    postJson("/session-refreshes", Map.of("refreshToken", rotated.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutAndAbsoluteRefreshExpiryInvalidateSessions() throws Exception {
    var tokens = login(phone());
    mvc.perform(delete("/api/v1/sessions/current").header("Authorization", access(tokens)))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/users/me").header("Authorization", access(tokens)))
        .andExpect(status().isUnauthorized());
    postJson("/session-refreshes", Map.of("refreshToken", tokens.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());
    var another = login(phone());
    clock.advance(Duration.ofDays(31));
    postJson("/session-refreshes", Map.of("refreshToken", another.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void permissionsAndBansAreCheckedAgainstTheDatabaseForEveryRequest() throws Exception {
    var tokens = login(phone());
    String uid = user(tokens), marker = UUID.randomUUID().toString();
    mvc.perform(get("/api/v1/admin/merchant-reviews").header("Authorization", access(tokens)))
        .andExpect(status().isForbidden());
    jdbc.update(
        "INSERT INTO campus_permission(code,name) VALUES('merchant:review',?) ON DUPLICATE KEY"
            + " UPDATE id=id",
        marker);
    Long permission =
        jdbc.queryForObject(
            "SELECT id FROM campus_permission WHERE code='merchant:review'", Long.class);
    jdbc.update("INSERT INTO campus_role(code,name) VALUES(?,?)", marker, marker);
    Long role = jdbc.queryForObject("SELECT id FROM campus_role WHERE code=?", Long.class, marker);
    jdbc.update(
        "INSERT INTO campus_role_permission(role_id,permission_id) VALUES(?,?)", role, permission);
    jdbc.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", uid, role);
    jdbc.update("UPDATE campus_user SET account_type='ADMIN' WHERE id=?", uid);
    mvc.perform(get("/api/v1/admin/merchant-reviews").header("Authorization", access(tokens)))
        .andExpect(status().isOk());
    jdbc.update("DELETE FROM campus_user_role WHERE user_id=?", uid);
    mvc.perform(get("/api/v1/admin/merchant-reviews").header("Authorization", access(tokens)))
        .andExpect(status().isForbidden());
    jdbc.update("UPDATE campus_user SET status='DISABLED' WHERE id=?", uid);
    mvc.perform(get("/api/v1/users/me").header("Authorization", access(tokens)))
        .andExpect(status().isUnauthorized());
    postJson("/session-refreshes", Map.of("refreshToken", tokens.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void onlyOwnProfileCanChangeAndUnknownFieldsAreRejected() throws Exception {
    var first = login(phone());
    var second = login(phone());
    String other = user(second);
    mvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", access(first))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"新昵称\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("新昵称"));
    mvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", access(first))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "nickname",
                            "越权修改",
                            "userId",
                            other,
                            "permissions",
                            List.of("merchant:review")))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"匿名\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/users/me").header("Authorization", access(second)))
        .andExpect(jsonPath("$.nickname").value(org.hamcrest.Matchers.not("新昵称")));
  }

  @Test
  void campusApplicationsAreEncryptedScopedAndDoNotAutoApprove() throws Exception {
    var tokens = login(phone());
    String uid = user(tokens);
    jdbc.update("INSERT INTO campus_school(name) VALUES(?)", UUID.randomUUID().toString());
    String school = jdbc.queryForObject("SELECT LAST_INSERT_ID()", String.class);
    var submitted =
        body(
            mvc.perform(
                    post("/api/v1/users/me/campus-verifications")
                        .header("Authorization", access(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "schoolId",
                                    school,
                                    "realName",
                                    "测试学生",
                                    "studentNumber",
                                    "STUDENT2026"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING")));
    String id = submitted.get("id").asText();
    assertThat(submitted.toString()).doesNotContain("测试学生", "STUDENT2026", "encryptedIdentity");
    assertThat(
            jdbc.queryForObject(
                "SELECT encrypted_identity FROM campus_campus_verification WHERE id=?",
                String.class,
                id))
        .doesNotContain("测试学生", "STUDENT2026")
        .contains(":");
    assertThat(jdbc.queryForObject("SELECT school_id FROM campus_user WHERE id=?", Long.class, uid))
        .isNull();
    mvc.perform(
            post("/api/v1/users/me/campus-verifications")
                .header("Authorization", access(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "schoolId",
                            school,
                            "realName",
                            "测试学生",
                            "studentNumber",
                            "STUDENT2026"))))
        .andExpect(status().isConflict());
    var other = login(phone());
    mvc.perform(
            get("/api/v1/users/me/campus-verifications/" + id)
                .header("Authorization", access(other)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/users/me/campus-verifications").header("Authorization", access(other)))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void missingAndFailingSmsProvidersReturn503WithoutIssuingUsableChallenges() throws Exception {
    when(sms.available()).thenReturn(false);
    mvc.perform(get("/api/v1/authentication-capabilities"))
        .andExpect(jsonPath("$.smsAvailable").value(false));
    postJson("/verification-challenges", Map.of("phone", phone(), "purpose", "LOGIN"))
        .andExpect(status().isServiceUnavailable());
    when(sms.available()).thenReturn(true);
    doThrow(new RuntimeException("provider-sensitive-message"))
        .when(sms)
        .sendVerificationCode(anyString(), anyString(), anyString(), any());
    String phone = phone();
    String response =
        postJson("/verification-challenges", Map.of("phone", phone, "purpose", "LOGIN"))
            .andExpect(status().isServiceUnavailable())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).doesNotContain("provider-sensitive-message", phone);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM campus_verification_challenge WHERE phone=?",
                String.class,
                phone))
        .isEqualTo("FAILED");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentCodeConsumptionAndRefreshReplayAreSerialized() throws Exception {
    String phone = phone(), ip = "test-ip-" + UUID.randomUUID();
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      var c =
          body(
              mvc.perform(
                      post("/api/v1/verification-challenges")
                          .with(
                              req -> {
                                req.setRemoteAddr(ip);
                                return req;
                              })
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              json.writeValueAsString(Map.of("phone", phone, "purpose", "LOGIN"))))
                  .andExpect(status().isCreated()));
      var input =
          Map.of("phone", phone, "challengeId", c.get("id").asText(), "code", codes.get(phone));
      CountDownLatch start = new CountDownLatch(1);
      Callable<MvcResult> login =
          () -> {
            start.await();
            return postJson("/sessions", input).andReturn();
          };
      var first = workers.submit(login);
      var second = workers.submit(login);
      start.countDown();
      var a = first.get(15, TimeUnit.SECONDS);
      var b = second.get(15, TimeUnit.SECONDS);
      assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
          .containsExactlyInAnyOrder(201, 401);
      var tokens =
          json.readTree(
              (a.getResponse().getStatus() == 201 ? a : b).getResponse().getContentAsString());
      var refreshInput = Map.of("refreshToken", tokens.get("refreshToken").asText());
      CountDownLatch refreshStart = new CountDownLatch(1);
      Callable<MvcResult> refresh =
          () -> {
            refreshStart.await();
            return postJson("/session-refreshes", refreshInput).andReturn();
          };
      var one = workers.submit(refresh);
      var two = workers.submit(refresh);
      refreshStart.countDown();
      var r1 = one.get(15, TimeUnit.SECONDS);
      var r2 = two.get(15, TimeUnit.SECONDS);
      assertThat(List.of(r1.getResponse().getStatus(), r2.getResponse().getStatus()))
          .containsExactlyInAnyOrder(201, 401);
      var rotated =
          json.readTree(
              (r1.getResponse().getStatus() == 201 ? r1 : r2).getResponse().getContentAsString());
      mvc.perform(get("/api/v1/users/me").header("Authorization", access(rotated)))
          .andExpect(status().isUnauthorized());
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, phone))
          .isEqualTo(1);
    } finally {
      workers.shutdownNow();
      workers.awaitTermination(15, TimeUnit.SECONDS);
      // Only committed rows created for this unique phone are removed. Existing data is untouched.
      jdbc.update(
          "DELETE FROM campus_refresh_token WHERE session_id IN (SELECT s.id FROM"
              + " campus_auth_session s JOIN campus_user u ON u.id=s.user_id WHERE u.phone=?)",
          phone);
      jdbc.update(
          "DELETE FROM campus_auth_session WHERE user_id IN (SELECT id FROM campus_user WHERE"
              + " phone=?)",
          phone);
      jdbc.update("DELETE FROM campus_user WHERE phone=?", phone);
      jdbc.update("DELETE FROM campus_verification_challenge WHERE phone=?", phone);
      for (String key :
          List.of("phone-cooldown:" + phone, "phone-window:" + phone, "ip-window:" + ip))
        jdbc.update(
            "DELETE FROM campus_verification_rate WHERE rate_key=?", secrets.hash("rate:" + key));
    }
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
    Instant value = Instant.now();

    void advance(Duration duration) {
      value = value.plus(duration);
    }

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }
}
