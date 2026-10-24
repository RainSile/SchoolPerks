package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

/** Real MySQL, real local SMS adapter, scoped fixtures rolled back after each test. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.integrations.sms.simulated-enabled=true",
      "campus.auth.code-length=4",
      "campus.auth.max-code-attempts=3",
      "campus.auth.password.phone-limit=3"
    })
@AutoConfigureMockMvc(
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("local")
@Transactional
@Import(AuthenticationMySqlTest.TimeConfiguration.class)
class PasswordAuthenticationMySqlTest {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CODE = String.format("%04d", RANDOM.nextInt(10000));
  private static final String PASSWORD = "Test-Password!2026",
      NEW_PASSWORD = "Changed-Password!2026";

  private static String key() {
    byte[] value = new byte[32];
    RANDOM.nextBytes(value);
    return Base64.getEncoder().encodeToString(value);
  }

  private static final String JWT = key(), OTP = key(), IDENTITY = key();

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) {
    r.add("campus.security.jwt.secret-base64", () -> JWT);
    r.add("campus.auth.code-hash-secret-base64", () -> OTP);
    r.add("campus.auth.identity-key-base64", () -> IDENTITY);
    r.add("campus.integrations.sms.simulated-code", () -> CODE);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthenticationMySqlTest.MutableClock clock;

  @BeforeEach
  void time() {
    clock.value = Instant.now();
  }

  private String phone() {
    String value;
    do {
      value = "138" + String.format("%08d", RANDOM.nextInt(100000000));
    } while (jdbc.queryForObject(
            "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, value)
        > 0);
    return value;
  }

  private ResultActions postJson(String path, Object input) throws Exception {
    return mvc.perform(
        post("/api/v1" + path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(input)));
  }

  private JsonNode body(ResultActions action) throws Exception {
    return json.readTree(action.andReturn().getResponse().getContentAsByteArray());
  }

  private String challenge(String phone, String purpose) throws Exception {
    clock.advance(Duration.ofSeconds(61));
    return body(postJson("/verification-challenges", Map.of("phone", phone, "purpose", purpose))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").doesNotExist()))
        .get("id")
        .asText();
  }

  private Map<String, String> registration(String phone, String challenge, String password) {
    return Map.of(
        "phone",
        phone,
        "challengeId",
        challenge,
        "code",
        CODE,
        "password",
        password,
        "nickname",
        "账号验收");
  }

  private JsonNode register(String phone) throws Exception {
    return body(
        postJson("/registrations", registration(phone, challenge(phone, "REGISTER"), PASSWORD))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store")));
  }

  private ResultActions login(String phone, String password) throws Exception {
    return postJson("/password-sessions", Map.of("phone", phone, "password", password));
  }

  private String bearer(JsonNode tokens) {
    return "Bearer " + tokens.get("accessToken").asText();
  }

  private long id(String phone) {
    return jdbc.queryForObject("SELECT id FROM campus_user WHERE phone=?", Long.class, phone);
  }

  private Map<String, String> reset(String phone, String challenge, String code) {
    return Map.of("phone", phone, "challengeId", challenge, "code", code, "password", NEW_PASSWORD);
  }

  @Test
  void registrationHashesPasswordAndOnlyCreatesOrdinaryUsers() throws Exception {
    String phone = phone();
    var tokens = register(phone);
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM campus_user WHERE phone=?", String.class, phone);
    assertThat(new BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();
    assertThat(hash).doesNotContain(PASSWORD);
    mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(tokens)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountType").value("USER"))
        .andExpect(jsonPath("$.homePath").value("/app"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    login(phone, PASSWORD).andExpect(status().isCreated());
    String duplicate = challenge(phone, "REGISTER");
    postJson("/registrations", registration(phone, duplicate, NEW_PASSWORD))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM campus_user WHERE phone=?", String.class, phone))
        .isEqualTo(hash);
  }

  @Test
  void clientCannotChooseARoleAndPasswordsMustMeetBytePolicy() throws Exception {
    String phone = phone(), c = challenge(phone, "REGISTER");
    var input = new HashMap<>(registration(phone, c, PASSWORD));
    input.put("accountType", "ADMIN");
    postJson("/registrations", input).andExpect(status().isBadRequest());
    postJson("/registrations", registration(phone, c, "short")).andExpect(status().isBadRequest());
    postJson("/registrations", registration(phone, c, "密".repeat(25)))
        .andExpect(status().isBadRequest());
    postJson("/registrations", registration(phone, c, PASSWORD)).andExpect(status().isCreated());
  }

  @Test
  void allThreeExistingAccountTypesUsePasswordLoginAndServerPortals() throws Exception {
    for (var role : List.of("USER", "MERCHANT", "ADMIN")) {
      String phone = phone();
      register(phone);
      jdbc.update("UPDATE campus_user SET account_type=? WHERE phone=?", role, phone);
      var tokens = body(login(phone, PASSWORD).andExpect(status().isCreated()));
      String path = Map.of("USER", "/app", "MERCHANT", "/merchant", "ADMIN", "/admin").get(role);
      mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(tokens)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accountType").value(role))
          .andExpect(jsonPath("$.homePath").value(path));
    }
  }

  @Test
  void invalidAbsentDisabledAndSmsOnlyAccountsReturnSameCredentialError() throws Exception {
    String phone = phone();
    register(phone);
    var wrong = body(login(phone, "Wrong-password!").andExpect(status().isUnauthorized()));
    var absent = body(login(phone(), PASSWORD).andExpect(status().isUnauthorized()));
    jdbc.update("UPDATE campus_user SET status='DISABLED' WHERE phone=?", phone);
    var disabled = body(login(phone, PASSWORD).andExpect(status().isUnauthorized()));
    jdbc.update("UPDATE campus_user SET status='ACTIVE',password_hash=NULL WHERE phone=?", phone);
    var noPassword = body(login(phone, PASSWORD).andExpect(status().isUnauthorized()));
    for (var response : List.of(absent, disabled, noPassword)) {
      assertThat(response.get("code")).isEqualTo(wrong.get("code"));
      assertThat(response.get("detail")).isEqualTo(wrong.get("detail"));
    }
  }

  @Test
  void passwordAttemptsAreRateLimitedEvenWhenCredentialsFail() throws Exception {
    String phone = phone();
    for (int i = 0; i < 3; i++) login(phone, PASSWORD).andExpect(status().isUnauthorized());
    login(phone, PASSWORD).andExpect(status().isTooManyRequests());
    clock.advance(Duration.ofMinutes(16));
    login(phone, PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void resettingPasswordRevokesEveryOldAccessAndRefreshTokenAndIsSingleUse() throws Exception {
    String phone = phone();
    var first = register(phone);
    var second = body(login(phone, PASSWORD).andExpect(status().isCreated()));
    String c = challenge(phone, "RESET_PASSWORD");
    postJson("/password-resets", reset(phone, c, CODE))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Cache-Control", "no-store"));
    for (var old : List.of(first, second)) {
      mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(old)))
          .andExpect(status().isUnauthorized());
      postJson("/session-refreshes", Map.of("refreshToken", old.get("refreshToken").asText()))
          .andExpect(status().isUnauthorized());
    }
    postJson("/password-resets", reset(phone, c, CODE)).andExpect(status().isUnauthorized());
    login(phone, PASSWORD).andExpect(status().isUnauthorized());
    login(phone, NEW_PASSWORD).andExpect(status().isCreated());
  }

  @Test
  void codesArePurposeBoundAndResetAttemptsExpireOrLock() throws Exception {
    String phone = phone();
    register(phone);
    String login = challenge(phone, "LOGIN");
    postJson("/password-resets", reset(phone, login, CODE)).andExpect(status().isUnauthorized());
    String c = challenge(phone, "RESET_PASSWORD"), wrong = CODE.equals("0000") ? "9999" : "0000";
    for (int i = 0; i < 3; i++)
      postJson("/password-resets", reset(phone, c, wrong)).andExpect(status().isUnauthorized());
    postJson("/password-resets", reset(phone, c, CODE)).andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT attempts FROM campus_verification_challenge WHERE id=?", Integer.class, c))
        .isEqualTo(3);
    clock.advance(Duration.ofMinutes(61));
    c = challenge(phone, "RESET_PASSWORD");
    clock.advance(Duration.ofMinutes(6));
    postJson("/password-resets", reset(phone, c, CODE)).andExpect(status().isUnauthorized());
    login(phone, PASSWORD).andExpect(status().isCreated());
  }

  @Test
  void loginCodeCannotRegisterAndRegisterCodeCannotLogin() throws Exception {
    String phone = phone(), c = challenge(phone, "LOGIN");
    postJson("/registrations", registration(phone, c, PASSWORD))
        .andExpect(status().isUnauthorized());
    String register = challenge(phone, "REGISTER");
    postJson("/sessions", Map.of("phone", phone, "challengeId", register, "code", CODE))
        .andExpect(status().isUnauthorized());
    // Creating another purpose does not supersede the valid login challenge.
    postJson("/sessions", Map.of("phone", phone, "challengeId", c, "code", CODE))
        .andExpect(status().isCreated());
  }

  @Test
  void changePasswordRequiresOldPasswordAndInvalidatesAllSessions() throws Exception {
    String phone = phone();
    var tokens = register(phone);
    mvc.perform(
            put("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        Map.of("currentPassword", PASSWORD, "password", NEW_PASSWORD))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put("/api/v1/users/me/password")
                .header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        Map.of("currentPassword", "wrong-password", "password", NEW_PASSWORD))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put("/api/v1/users/me/password")
                .header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        Map.of("currentPassword", PASSWORD, "password", NEW_PASSWORD))))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(tokens)))
        .andExpect(status().isUnauthorized());
    postJson("/session-refreshes", Map.of("refreshToken", tokens.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());
    login(phone, NEW_PASSWORD).andExpect(status().isCreated());
  }

  @Test
  void localAdapterExposesModeNotCodeAndStoresOnlyHmac() throws Exception {
    mvc.perform(get("/api/v1/authentication-capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.smsSimulated").value(true))
        .andExpect(jsonPath("$.codeLength").value(4))
        .andExpect(jsonPath("$.simulatedCode").doesNotExist());
    String c = challenge(phone(), "REGISTER");
    assertThat(
            jdbc.queryForObject(
                "SELECT code_hash FROM campus_verification_challenge WHERE id=?", String.class, c))
        .hasSize(64)
        .isNotEqualTo(CODE);
  }
}
