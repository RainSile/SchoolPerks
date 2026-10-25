package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.campus.perks.boot.support.BusinessDatasetCleanup;
import com.campus.perks.user.service.AuthSecrets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

/**
 * Real Vue -> HTTP -> Spring security/services -> MySQL. Isolated committed fixtures are removed by
 * exact ownership roots and verified by the phase's complete database snapshot.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "campus.auth.enabled=true", "campus.security.jwt.enabled=true",
      "campus.notifications.dispatch-enabled=false", "campus.search.rebuild-enabled=false",
      "campus.trade.scheduler-enabled=false"
    })
@EnabledIfEnvironmentVariables({
  @EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true"),
  @EnabledIfEnvironmentVariable(named = "CAMPUS_BUSINESS_EVIDENCE_DIR", matches = ".+"),
  @EnabledIfEnvironmentVariable(named = "CAMPUS_LIVE_BROWSER_TEST", matches = "true")
})
class BusinessLiveJourneyMySqlTest extends BusinessReadChainsMySqlTest {
  private static final int FRONTEND_PORT = freePort();
  @LocalServerPort int backendPort;
  @Autowired DataSource dataSource;
  @Autowired AuthSecrets secrets;
  boolean committed;
  BusinessDatasetCleanup dataset;
  final List<String> phones = new ArrayList<>();

  private static int freePort() {
    try (var socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void liveOrigin(DynamicPropertyRegistry registry) {
    registry.add("campus.security.allowed-origins", () -> "http://127.0.0.1:" + FRONTEND_PORT);
  }

  @Test
  void businessRealBrowserCouponJourney() throws Exception {
    runBrowserJourney(false);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_coupon c JOIN campus_coupon_claim cl ON"
                    + " cl.coupon_id=c.id WHERE c.merchant_id=? AND cl.user_id=?",
                Integer.class,
                store,
                user))
        .isOne();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_redemption r JOIN campus_coupon_claim cl ON"
                    + " cl.id=r.claim_id JOIN campus_coupon c ON c.id=cl.coupon_id WHERE"
                    + " c.merchant_id=? AND cl.user_id=?",
                Integer.class,
                store,
                user))
        .isOne();
  }

  protected void runBrowserJourney(boolean mapOnly) throws Exception {
    String password = "Journey-" + UUID.randomUUID() + "!26";
    String hash = new BCryptPasswordEncoder().encode(password);
    for (long actor : List.of(user, merchant, admin)) {
      String phone;
      do {
        phone = "139" + String.format("%08d", new SecureRandom().nextInt(100000000));
      } while (db.queryForObject(
              "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, phone)
          != 0);
      phones.add(phone);
      db.update("UPDATE campus_user SET phone=?,password_hash=? WHERE id=?", phone, hash, actor);
    }
    dataset =
        new BusinessDatasetCleanup(
            dataSource,
            Map.of(
                "campus_user",
                List.of(user, merchant, admin, unrelatedMerchant, restrictedAdmin),
                "campus_school",
                List.of(school),
                "campus_role",
                List.of(role)));
    TestTransaction.flagForCommit();
    TestTransaction.end();
    committed = true;
    Path evidence = Path.of(System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"));
    Path root = evidence;
    while (root != null && !Files.exists(root.resolve("frontend/package.json")))
      root = root.getParent();
    if (root == null) throw new IllegalStateException("Project root not found");
    ProcessBuilder command =
        new ProcessBuilder(
            "node", root.resolve("frontend/scripts/live-business-browser-check.mjs").toString());
    command
        .directory(root.toFile())
        .redirectErrorStream(true)
        .redirectOutput(evidence.resolve("browser-live.log").toFile());
    Map<String, String> environment = command.environment();
    environment.put("CAMPUS_LIVE_BACKEND_PORT", "" + backendPort);
    environment.put("CAMPUS_LIVE_MAP_ONLY", "" + mapOnly);
    environment.put("CAMPUS_LIVE_FRONTEND_PORT", "" + FRONTEND_PORT);
    environment.put("CAMPUS_LIVE_PASSWORD", password);
    environment.put("CAMPUS_LIVE_USER_PHONE", phones.get(0));
    environment.put("CAMPUS_LIVE_MERCHANT_PHONE", phones.get(1));
    environment.put("CAMPUS_LIVE_ADMIN_PHONE", phones.get(2));
    environment.put("CAMPUS_LIVE_MERCHANT_ID", "" + store);
    environment.put("CAMPUS_LIVE_SCHOOL_ID", "" + school);
    environment.putIfAbsent(
        "CAMPUS_PLAYWRIGHT_MODULE",
        "C:\\Users\\r"
            + "ains\\.cache\\codex-runtimes\\codex-primary-runtime\\dependencies\\n"
            + "ode\\n"
            + "ode_modules\\playwright");
    environment.putIfAbsent(
        "CAMPUS_BROWSER_EXECUTABLE", "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
    Process process = command.start();
    boolean completed = process.waitFor(180, TimeUnit.SECONDS);
    if (!completed) process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
    assertThat(completed).as("Live browser journey timeout").isTrue();
    assertThat(process.exitValue())
        .as("Live browser journey; inspect private browser-live.log")
        .isZero();
  }

  @AfterEach
  void cleanOnlyCommittedJourney() throws Exception {
    if (!committed) return;
    if (TestTransaction.isActive()) TestTransaction.end();
    dataset.clean();
    for (String phone : phones)
      db.update(
          "DELETE FROM campus_verification_rate WHERE rate_key=?",
          secrets.hash("rate:password-phone:" + phone));
    db.update(
        "DELETE FROM campus_verification_rate WHERE rate_key=?",
        secrets.hash("rate:password-ip:127.0.0.1"));
  }
}
