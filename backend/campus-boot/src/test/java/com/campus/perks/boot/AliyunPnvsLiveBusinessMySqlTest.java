package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.campus.perks.integration.adapter.AliyunPnvsSmsAdapter;
import com.campus.perks.integration.config.AliyunPnvsProperties;
import com.campus.perks.integration.port.SmsSenderPort;
import com.fasterxml.jackson.databind.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in real PNVS business verification: one allowed recipient, three sends, rolled-back SQL. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_PNVS_LIVE_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
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
@Import(AliyunPnvsLiveBusinessMySqlTest.LiveConfiguration.class)
class AliyunPnvsLiveBusinessMySqlTest {
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

  @TestConfiguration(proxyBeanMethods = false)
  static class LiveConfiguration {
    @Bean
    LiveClient liveClient() throws Exception {
      return new LiveClient();
    }

    @Bean
    @Primary
    SmsSenderPort liveSms(LiveClient client, ObjectMapper json) {
      var p =
          new AliyunPnvsProperties(
              true,
              "https://dypnsapi.aliyuncs.com",
              required("ALIYUN_PNVS_ACCESS_KEY_ID"),
              required("ALIYUN_PNVS_ACCESS_KEY_SECRET"),
              required("ALIYUN_PNVS_SIGN_NAME"),
              "86",
              "100001",
              "100001",
              "100003",
              "campus-login",
              "campus-register",
              "campus-reset",
              Duration.ofSeconds(3),
              Duration.ofSeconds(5));
      return new AliyunPnvsSmsAdapter(p, json, client);
    }
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Missing live SMS setting: " + name);
    return value;
  }

  /**
   * Only this opt-in test requests upstream debug output; the business adapter always requests
   * false.
   */
  static final class LiveClient extends Client {
    final Map<String, String> codes = new ConcurrentHashMap<>();
    int sends;

    LiveClient() throws Exception {
      super(
          new com.aliyun.teaopenapi.models.Config()
              .setAccessKeyId(required("ALIYUN_PNVS_ACCESS_KEY_ID"))
              .setAccessKeySecret(required("ALIYUN_PNVS_ACCESS_KEY_SECRET"))
              .setEndpoint("dypnsapi.aliyuncs.com")
              .setProtocol("HTTPS")
              .setConnectTimeout(3000)
              .setReadTimeout(5000)
              .setHttpsProxy(System.getenv("CAMPUS_SMS_TEST_PROXY")));
    }

    @Override
    public SendSmsVerifyCodeResponse sendSmsVerifyCodeWithOptions(
        SendSmsVerifyCodeRequest request, RuntimeOptions options) throws Exception {
      if (!required("CAMPUS_SMS_TEST_PHONE").equals(request.getPhoneNumber()))
        throw new IllegalStateException("Live recipient is not allowlisted");
      if (!Boolean.FALSE.equals(request.getReturnVerifyCode())
          || request.getAutoRetry() != 0
          || Boolean.TRUE.equals(options.getAutoretry()))
        throw new IllegalStateException("Unsafe live send flags");
      reserveBudget();
      sends++;
      request.setReturnVerifyCode(true);
      try {
        var response = super.sendSmsVerifyCodeWithOptions(request, options);
        if (response.getBody() != null && response.getBody().getModel() != null) {
          var model = response.getBody().getModel();
          String code = model.getVerifyCode();
          if (code != null) codes.put(request.getOutId(), code);
          model.setVerifyCode(null);
        }
        return response;
      } catch (com.aliyun.tea.TeaException e) {
        String code = e.getCode();
        if (code == null || !code.matches("[A-Za-z0-9_.-]{1,80}")) code = "UNCLASSIFIED";
        Files.writeString(Path.of(required("CAMPUS_SMS_SEND_LEDGER") + ".last-error"), code);
        throw e;
      } finally {
        request.setReturnVerifyCode(false);
      }
    }

    private void reserveBudget() throws Exception {
      Path file = Path.of(required("CAMPUS_SMS_SEND_LEDGER"));
      if (!file.isAbsolute() || !file.toString().contains(".local"))
        throw new IllegalStateException("Private absolute ledger path required");
      try (var channel =
              FileChannel.open(
                  file,
                  StandardOpenOption.CREATE,
                  StandardOpenOption.READ,
                  StandardOpenOption.WRITE);
          var lock = channel.lock()) {
        ByteBuffer bytes = ByteBuffer.allocate(128);
        channel.read(bytes);
        bytes.flip();
        String text = StandardCharsets.UTF_8.decode(bytes).toString().trim();
        int reserved = text.isEmpty() ? 0 : Integer.parseInt(text);
        // Reserve3 SMS segments per attempt, even failures, so long templates stay under50.
        if (reserved + 3 > 50)
          throw new IllegalStateException("User-authorized SMS test budget exhausted");
        channel.position(0);
        channel.truncate(0);
        channel.write(StandardCharsets.UTF_8.encode(Integer.toString(reserved + 3)));
        channel.force(true);
      }
    }

    String code(String challenge) {
      String code = codes.get(challenge);
      if (code == null)
        throw new AssertionError("Upstream did not provide the privately requested test code");
      return code;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired LiveClient client;
  private long lastSendAt;

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
    // Honor both local and provider resend limits; never clear another user's rate counters.
    while (lastSendAt > 0 && System.currentTimeMillis() - lastSendAt < 61000) Thread.sleep(1000);
    lastSendAt = System.currentTimeMillis();
    var response =
        post("/verification-challenges", Map.of("phone", phone, "purpose", purpose))
            .andExpect(status().isCreated());
    var data = body(response);
    String id = data.path("id").asText();
    if (response.andReturn().getResponse().getContentAsString().contains(client.code(id)))
      throw new AssertionError("Public HTTP response leaked a code");
    assertThat(
            db.queryForObject(
                "SELECT code_hash FROM campus_verification_challenge WHERE id=?", String.class, id))
        .isNull();
    return id;
  }

  @Test
  void realRegisterLoginAndPasswordResetVerifyWithPnvsAndRevokeSessions() throws Exception {
    String phone = required("CAMPUS_SMS_TEST_PHONE");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, phone))
        .as("Use a recipient with no existing local business account")
        .isZero();
    String password = "Live-" + UUID.randomUUID() + "!",
        replacement = "Reset-" + UUID.randomUUID() + "!";
    String registration = challenge(phone, "REGISTER");
    String wrong = client.code(registration).equals("000000") ? "999999" : "000000";
    post(
            "/registrations",
            Map.of(
                "phone",
                phone,
                "challengeId",
                registration,
                "code",
                wrong,
                "nickname",
                "短信接入验收",
                "password",
                password))
        .andExpect(status().isUnauthorized());
    var account =
        body(
            post(
                    "/registrations",
                    Map.of(
                        "phone",
                        phone,
                        "challengeId",
                        registration,
                        "code",
                        client.code(registration),
                        "nickname",
                        "短信接入验收",
                        "password",
                        password))
                .andExpect(status().isCreated()));
    post("/password-sessions", Map.of("phone", phone, "password", password))
        .andExpect(status().isCreated());
    String login = challenge(phone, "LOGIN");
    var token =
        body(
            post(
                    "/sessions",
                    Map.of("phone", phone, "challengeId", login, "code", client.code(login)))
                .andExpect(status().isCreated()));
    post("/sessions", Map.of("phone", phone, "challengeId", login, "code", client.code(login)))
        .andExpect(status().isUnauthorized());
    String reset = challenge(phone, "RESET_PASSWORD");
    post(
            "/password-resets",
            Map.of(
                "phone",
                phone,
                "challengeId",
                reset,
                "code",
                client.code(reset),
                "password",
                replacement))
        .andExpect(status().isNoContent());
    post("/password-sessions", Map.of("phone", phone, "password", password))
        .andExpect(status().isUnauthorized());
    post("/password-sessions", Map.of("phone", phone, "password", replacement))
        .andExpect(status().isCreated());
    for (var t : List.of(account, token))
      mvc.perform(
              get("/api/v1/users/me")
                  .header("Authorization", "Bearer " + t.path("accessToken").asText()))
          .andExpect(status().isUnauthorized());
    assertThat(client.sends).isEqualTo(3);
    Files.writeString(
        Path.of(required("CAMPUS_SMS_SEND_LEDGER") + ".success"),
        "Real business registration, login, reset, replay rejection and session revocation passed;"
            + " sends=3");
    client.codes.clear();
  }
}
