package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.SmsSenderPort;
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
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real MySQL transactions and real HTTP/auth filters; provider is mocked, so sends cost nothing.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.auth.max-code-attempts=3",
      "campus.integrations.sms.simulated-enabled=false",
      "campus.integrations.sms.aliyun-pnvs.enabled=false",
      "campus.trade.scheduler-enabled=false",
      "campus.notifications.dispatch-enabled=false",
      "campus.search.rebuild-enabled=false"
    })
@AutoConfigureMockMvc(
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("local")
@Transactional
@Import(AuthenticationMySqlTest.TimeConfiguration.class)
class RemoteSmsAuthenticationMySqlTest {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CODE = String.format("%06d", RANDOM.nextInt(1000000));
  private static final String PASSWORD = "Fixture-Password!2026", NEW_PASSWORD = "Fixture-New!2026";

  private static String key() {
    byte[] b = new byte[32];
    RANDOM.nextBytes(b);
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
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AuthenticationMySqlTest.MutableClock clock;
  @MockitoBean SmsSenderPort sms;

  @BeforeEach
  void setup() {
    clock.value = Instant.now();
    when(sms.available()).thenReturn(true);
    when(sms.verifiesRemotely()).thenReturn(true);
    when(sms.verifierType()).thenReturn("ALIYUN_PNVS");
    when(sms.startRemoteVerification(any()))
        .thenAnswer(
            call -> {
              SmsSenderPort.RemoteChallenge c = call.getArgument(0);
              return new SmsSenderPort.Receipt(
                  "ALIYUN_PNVS", "ref-" + c.id(), "scope-" + c.purpose());
            });
    when(sms.verifyRemoteCode(
            anyString(), anyString(), anyString(), anyString(), anyString(), eq(CODE)))
        .thenReturn(true);
  }

  private String phone() {
    String p;
    do {
      p = "139" + String.format("%08d", RANDOM.nextInt(100000000));
    } while (db.queryForObject("SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, p)
        > 0);
    return p;
  }

  private ResultActions post(String path, Object body) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1" + path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(body)));
  }

  private JsonNode body(ResultActions a) throws Exception {
    return json.readTree(a.andReturn().getResponse().getContentAsByteArray());
  }

  private String challenge(String phone, String purpose) throws Exception {
    clock.advance(Duration.ofSeconds(61));
    return body(post("/verification-challenges", Map.of("phone", phone, "purpose", purpose))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.providerReference").doesNotExist()))
        .path("id")
        .asText();
  }

  private ResultActions login(String phone, String id, String code) throws Exception {
    return post("/sessions", Map.of("phone", phone, "challengeId", id, "code", code));
  }

  @Test
  void registrationLoginResetAndSessionRevocationUseRemoteVerification() throws Exception {
    String phone = phone(), registration = challenge(phone, "REGISTER");
    assertThat(
            db.queryForObject(
                "SELECT code_hash FROM campus_verification_challenge WHERE id=?",
                String.class,
                registration))
        .isNull();
    var registered =
        body(
            post(
                    "/registrations",
                    Map.of(
                        "phone",
                        phone,
                        "challengeId",
                        registration,
                        "code",
                        CODE,
                        "nickname",
                        "远程核验测试",
                        "password",
                        PASSWORD))
                .andExpect(status().isCreated()));
    assertThat(
            db.queryForObject(
                "SELECT account_type FROM campus_user WHERE phone=?", String.class, phone))
        .isEqualTo("USER");
    String login = challenge(phone, "LOGIN");
    login(phone, login, CODE).andExpect(status().isCreated());
    login(phone, login, CODE).andExpect(status().isUnauthorized());
    String reset = challenge(phone, "RESET_PASSWORD");
    post(
            "/password-resets",
            Map.of("phone", phone, "challengeId", reset, "code", CODE, "password", NEW_PASSWORD))
        .andExpect(status().isNoContent());
    post("/password-sessions", Map.of("phone", phone, "password", PASSWORD))
        .andExpect(status().isUnauthorized());
    post("/password-sessions", Map.of("phone", phone, "password", NEW_PASSWORD))
        .andExpect(status().isCreated());
    mvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer " + registered.path("accessToken").asText()))
        .andExpect(status().isUnauthorized());
    verify(sms)
        .verifyRemoteCode(
            eq(phone),
            eq("REGISTER"),
            eq("scope-REGISTER"),
            anyString(),
            eq(registration),
            eq(CODE));
    verify(sms)
        .verifyRemoteCode(
            eq(phone), eq("LOGIN"), eq("scope-LOGIN"), anyString(), eq(login), eq(CODE));
    verify(sms)
        .verifyRemoteCode(
            eq(phone),
            eq("RESET_PASSWORD"),
            eq("scope-RESET_PASSWORD"),
            anyString(),
            eq(reset),
            eq(CODE));
  }

  @Test
  void crossPhoneAndCrossPurposeAreRejectedBeforeProviderCalls() throws Exception {
    String phone = phone(), id = challenge(phone, "REGISTER");
    clearInvocations(sms);
    login(phone, id, CODE).andExpect(status().isUnauthorized());
    post(
            "/registrations",
            Map.of(
                "phone",
                phone(),
                "challengeId",
                id,
                "code",
                CODE,
                "nickname",
                "隔离测试",
                "password",
                PASSWORD))
        .andExpect(status().isUnauthorized());
    verify(sms, never())
        .verifyRemoteCode(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void failedCodesAreCountedAndLockedWithoutFurtherProviderCalls() throws Exception {
    String phone = phone(), id = challenge(phone, "LOGIN");
    String wrong = CODE.equals("000000") ? "999999" : "000000";
    for (int i = 0; i < 4; i++) login(phone, id, wrong).andExpect(status().isUnauthorized());
    assertThat(
            db.queryForObject(
                "SELECT attempts FROM campus_verification_challenge WHERE id=?", Integer.class, id))
        .isEqualTo(3);
    verify(sms, times(3))
        .verifyRemoteCode(eq(phone), eq("LOGIN"), anyString(), anyString(), eq(id), eq(wrong));
  }

  @Test
  void expiredAndSupersededChallengesNeverReachProvider() throws Exception {
    String phone = phone(), old = challenge(phone, "LOGIN"), fresh = challenge(phone, "LOGIN");
    clearInvocations(sms);
    login(phone, old, CODE).andExpect(status().isUnauthorized());
    clock.advance(Duration.ofMinutes(6));
    login(phone, fresh, CODE).andExpect(status().isUnauthorized());
    verify(sms, never())
        .verifyRemoteCode(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void unavailableOrChangedVerifierCannotFallBackToLocalHash() throws Exception {
    String phone = phone(), id = challenge(phone, "LOGIN");
    when(sms.verifiesRemotely()).thenReturn(false);
    login(phone, id, CODE).andExpect(status().isServiceUnavailable());
    when(sms.verifiesRemotely()).thenReturn(true);
    when(sms.verifierType()).thenReturn("ANOTHER_PROVIDER");
    login(phone, id, CODE).andExpect(status().isServiceUnavailable());
    assertThat(
            db.queryForObject(
                "SELECT attempts FROM campus_verification_challenge WHERE id=?", Integer.class, id))
        .isZero();
    verify(sms, never())
        .verifyRemoteCode(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void providerFailureDoesNotSpendCodeAttemptsOrExposePrivateErrors() throws Exception {
    String phone = phone(), id = challenge(phone, "LOGIN");
    when(sms.verifyRemoteCode(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new ApplicationException(
                HttpStatus.SERVICE_UNAVAILABLE, "SMS_PROVIDER_UNAVAILABLE", "短信服务暂不可用"));
    login(phone, id, CODE).andExpect(status().isServiceUnavailable());
    assertThat(
            db.queryForObject(
                "SELECT attempts FROM campus_verification_challenge WHERE id=?", Integer.class, id))
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT status FROM campus_verification_challenge WHERE id=?", String.class, id))
        .isEqualTo("SENT");
  }

  @Test
  void unsuccessfulDeliveryIsRecordedAndCannotBecomeALoginChallenge() throws Exception {
    String phone = phone();
    doThrow(new IllegalStateException("fixture-private-detail"))
        .when(sms)
        .startRemoteVerification(any());
    var result =
        post("/verification-challenges", Map.of("phone", phone, "purpose", "LOGIN"))
            .andExpect(status().isServiceUnavailable());
    assertThat(result.andReturn().getResponse().getContentAsString())
        .doesNotContain("fixture-private-detail");
    assertThat(
            db.queryForObject(
                "SELECT status FROM campus_verification_challenge WHERE phone=?",
                String.class,
                phone))
        .isEqualTo("FAILED");
  }
}
