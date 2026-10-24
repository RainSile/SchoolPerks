package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.security.service.AccessTokenIssuer;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.notifications.dispatch-enabled=false",
      "campus.search.rebuild-enabled=false",
      "campus.trade.scheduler-enabled=false"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
@Transactional
class RbacIsolationMySqlTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenIssuer issuer;

  private static String secret() {
    byte[] b = new byte[32];
    new java.security.SecureRandom().nextBytes(b);
    return Base64.getEncoder().encodeToString(b);
  }

  private static final String JWT = secret(), OTP = secret(), IDENTITY = secret();

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) {
    r.add("campus.security.jwt.secret-base64", () -> JWT);
    r.add("campus.auth.code-hash-secret-base64", () -> OTP);
    r.add("campus.auth.identity-key-base64", () -> IDENTITY);
  }

  long admin, user, merchant, role;

  long insert(String sql, Object... values) {
    var k = new org.springframework.jdbc.support.GeneratedKeyHolder();
    db.update(
        c -> {
          var p = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
          return p;
        },
        k);
    return k.getKey().longValue();
  }

  long account(String type) {
    return insert(
        "INSERT INTO campus_user(phone,nickname,account_type) VALUES(?,'rbac-test',?)",
        "test-" + UUID.randomUUID().toString().substring(0, 20),
        type);
  }

  @BeforeEach
  void setup() {
    admin = account("ADMIN");
    user = account("USER");
    merchant = account("MERCHANT");
    role =
        insert(
            "INSERT INTO campus_role(code,name,account_type) VALUES(?,'manager','ADMIN')",
            "TEST_" + UUID.randomUUID());
    db.update(
        "INSERT INTO campus_role_permission(role_id,permission_id) SELECT ?,id FROM"
            + " campus_permission WHERE code='rbac:manage'",
        role);
    db.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", admin, role);
  }

  String token(long user) {
    var now = Instant.now();
    String sid = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version) SELECT"
            + " ?,id,?, ?,access_version FROM campus_user WHERE id=?",
        sid,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now.plusSeconds(3600)),
        user);
    return "Bearer "
        + issuer.issue(
            "" + user, sid, List.of("rbac:manage", "PORTAL_ADMIN"), now, now.plusSeconds(600));
  }

  ResultActions call(String method, String path, String token, Object data) throws Exception {
    var req =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/api/v1" + path)
            .header("Authorization", token);
    if (data != null)
      req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
    return mvc.perform(req);
  }

  JsonNode body(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  @Test
  void profilesExposeExclusiveServerPortalAndIgnoreForgedJwtPermissions() throws Exception {
    call("GET", "/users/me", token(user), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountType").value("USER"))
        .andExpect(jsonPath("$.homePath").value("/app"));
    call("GET", "/users/me", token(merchant), null)
        .andExpect(jsonPath("$.homePath").value("/merchant"));
    call("GET", "/users/me", token(admin), null).andExpect(jsonPath("$.homePath").value("/admin"));
    call("GET", "/admin/accounts", token(user), null).andExpect(status().isForbidden());
  }

  @Test
  void privateApisRejectOtherPortalsEvenWhenPathsAreManuallyEntered() throws Exception {
    call("GET", "/orders", token(merchant), null).andExpect(status().isForbidden());
    call("GET", "/users/me/merchants", token(user), null).andExpect(status().isForbidden());
    call("GET", "/users/me/posts", token(admin), null).andExpect(status().isForbidden());
    call("GET", "/admin/accounts", token(merchant), null).andExpect(status().isForbidden());
  }

  @Test
  void roleCrudChecksVersionsAndUnknownPermissions() throws Exception {
    String auth = token(admin);
    var r =
        body(
            call(
                    "POST",
                    "/admin/roles",
                    auth,
                    Map.of(
                        "code",
                        "CUSTOM_ROLE",
                        "name",
                        "审核员",
                        "accountType",
                        "ADMIN",
                        "permissions",
                        List.of("community:moderate")))
                .andExpect(status().isCreated()));
    String id = r.get("id").asText();
    call(
            "PATCH",
            "/admin/roles/" + id,
            auth,
            Map.of("version", 1, "name", "修改", "permissions", List.of()))
        .andExpect(status().isConflict());
    call(
            "PATCH",
            "/admin/roles/" + id,
            auth,
            Map.of("version", 0, "name", "修改", "permissions", List.of("PORTAL_ADMIN")))
        .andExpect(status().isUnprocessableEntity());
    call(
            "PATCH",
            "/admin/roles/" + id,
            auth,
            Map.of("version", 0, "name", "修改", "permissions", List.of("audit:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    call("DELETE", "/admin/roles/" + id + "?version=1", auth, null)
        .andExpect(status().isNoContent());
  }

  @Test
  void roleAssignmentCannotCrossPortalAndCannotAlterSelf() throws Exception {
    String auth = token(admin);
    call(
            "PUT",
            "/admin/accounts/" + user + "/roles",
            auth,
            Map.of("version", 0, "roleIds", List.of("" + role)))
        .andExpect(status().isUnprocessableEntity());
    call(
            "PATCH",
            "/admin/accounts/" + admin,
            auth,
            Map.of("version", 0, "accountType", "USER", "status", "ACTIVE"))
        .andExpect(status().isConflict());
    call(
            "PATCH",
            "/admin/roles/" + role,
            auth,
            Map.of("version", 0, "name", "self", "permissions", List.of()))
        .andExpect(status().isConflict());
  }

  @Test
  void accountPortalChangesRevokeOldSessionAndNewSessionUsesNewHome() throws Exception {
    String stale = token(user);
    call(
            "PATCH",
            "/admin/accounts/" + user,
            token(admin),
            Map.of("version", 0, "accountType", "MERCHANT", "status", "ACTIVE"))
        .andExpect(status().isOk());
    call("GET", "/users/me", stale, null).andExpect(status().isUnauthorized());
    call("GET", "/users/me", token(user), null)
        .andExpect(jsonPath("$.homePath").value("/merchant"));
  }

  @Test
  void disabledAccountsCanBeReenabledByAnotherAdministrator() throws Exception {
    db.update("UPDATE campus_user SET status='DISABLED' WHERE id=?", user);
    call(
            "PATCH",
            "/admin/accounts/" + user,
            token(admin),
            Map.of("version", 0, "accountType", "USER", "status", "ACTIVE"))
        .andExpect(status().isOk());
    assertThat(db.queryForObject("SELECT status FROM campus_user WHERE id=?", String.class, user))
        .isEqualTo("ACTIVE");
  }
}
