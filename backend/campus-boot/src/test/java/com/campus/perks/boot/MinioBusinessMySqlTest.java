package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.file.config.FileProperties;
import com.campus.perks.integration.port.ObjectStoragePort;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real private MinIO + authorized MySQL; database fixtures roll back and exact object keys are
 * removed.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MINIO_LIVE_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.integrations.minio.enabled=true",
      "campus.files.max-bytes=256",
      "campus.integrations.minio.max-object-bytes=256"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
@Transactional
class MinioBusinessMySqlTest {
  private static final String SECRET = secret();

  private static String secret() {
    byte[] value = new byte[32];
    new SecureRandom().nextBytes(value);
    return Base64.getEncoder().encodeToString(value);
  }

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) {
    r.add("campus.security.jwt.secret-base64", () -> SECRET);
    r.add("campus.auth.code-hash-secret-base64", () -> SECRET);
    r.add("campus.auth.identity-key-base64", () -> SECRET);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenIssuer issuer;
  @Autowired ObjectStoragePort storage;
  @Autowired FileProperties files;
  private final List<String> ids = new ArrayList<>(), extraKeys = new ArrayList<>();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private long owner, other, merchant, admin;

  private long insert(String sql, Object... values) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
          return statement;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  private long user(String type) {
    String phone;
    do {
      phone = "199" + java.util.concurrent.ThreadLocalRandom.current().nextInt(10000000, 100000000);
    } while (jdbc.queryForObject(
            "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, phone)
        != 0);
    long id =
        insert(
            "INSERT INTO campus_user(phone,nickname) VALUES(?,?)",
            phone,
            "minio-live-" + UUID.randomUUID());
    jdbc.update("UPDATE campus_user SET account_type=? WHERE id=?", type, id);
    return id;
  }

  private void grant(long actor, String permission) {
    Long permissionId =
        jdbc.query(
            "SELECT id FROM campus_permission WHERE code=?",
            result -> result.next() ? result.getLong(1) : null,
            permission);
    if (permissionId == null)
      permissionId =
          insert("INSERT INTO campus_permission(code,name) VALUES(?,?)", permission, "minio-live");
    long role =
        insert(
            "INSERT INTO campus_role(code,name) VALUES(?,?)",
            UUID.randomUUID().toString(),
            "minio-live");
    jdbc.update(
        "INSERT INTO campus_role_permission(role_id,permission_id) VALUES(?,?)",
        role,
        permissionId);
    jdbc.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", actor, role);
  }

  @BeforeEach
  void setup() {
    assertThat(storage.available()).as("Real private MinIO must be reachable").isTrue();
    owner = user("USER");
    other = user("USER");
    merchant = user("MERCHANT");
    admin = user("ADMIN");
  }

  @AfterEach
  void cleanupObjects() {
    for (String id : ids) {
      var keys =
          jdbc.queryForMap(
              "SELECT staging_key,immutable_key FROM campus_file_object WHERE id=?", id);
      storage.delete(keys.get("staging_key").toString());
      storage.delete(keys.get("immutable_key").toString());
    }
    for (String key : extraKeys) storage.delete(key);
  }

  private String token(long user) {
    String sid = UUID.randomUUID().toString();
    var now = Instant.now();
    jdbc.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version)"
            + " VALUES(?,?,?,?,(SELECT access_version FROM campus_user WHERE id=?))",
        sid,
        user,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now.plus(Duration.ofHours(1))),
        user);
    return "Bearer " + issuer.issue(Long.toString(user), sid, List.of(), now, now.plusSeconds(900));
  }

  private ResultActions post(String route, long actor, Object value) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1" + route)
            .header("Authorization", token(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(value)));
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
  }

  private JsonNode ticket(String type, int size, String purpose) throws Exception {
    var value =
        body(
            post(
                    "/uploads",
                    owner,
                    Map.of(
                        "fileName",
                        "minio-live-test",
                        "contentType",
                        type,
                        "size",
                        size,
                        "purpose",
                        purpose))
                .andExpect(status().isCreated()));
    ids.add(value.path("id").asText());
    return value;
  }

  private ResultActions complete(String id, long actor) throws Exception {
    return mvc.perform(
        put("/api/v1/uploads/" + id + "/completion").header("Authorization", token(actor)));
  }

  private URI download(String id) throws Exception {
    return URI.create(
        body(post("/uploads/" + id + "/download-tickets", owner, Map.of())
                .andExpect(status().isOk()))
            .path("url")
            .asText());
  }

  private int putBytes(URI url, String type, byte[] data) throws Exception {
    return http.send(
            HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", type)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }

  private byte[] bytes(URI url) throws Exception {
    var response =
        http.send(
            HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  @Test
  void realQualificationUploadIsSealedOwnedAndAcceptedByMerchantBusiness() throws Exception {
    byte[] original = "%PDF-1.7\nold".getBytes(StandardCharsets.US_ASCII);
    var ticket = ticket("application/pdf", original.length, "MERCHANT_QUALIFICATION");
    String id = ticket.path("id").asText();
    URI upload = URI.create(ticket.path("uploadUrl").asText());
    assertThat(putBytes(upload, "application/pdf", original)).isEqualTo(200);
    complete(id, owner).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
    assertThat(bytes(download(id))).isEqualTo(original);
    assertThat(
            putBytes(
                upload, "application/pdf", "%PDF-1.7\nnew".getBytes(StandardCharsets.US_ASCII)))
        .isEqualTo(200);
    complete(id, owner).andExpect(status().isOk());
    assertThat(bytes(download(id)))
        .as("Replaying staging writes cannot replace the sealed snapshot")
        .isEqualTo(original);
    for (long actor : new long[] {other, merchant, admin}) {
      mvc.perform(get("/api/v1/uploads/" + id).header("Authorization", token(actor)))
          .andExpect(status().isNotFound());
      complete(id, actor).andExpect(status().isNotFound());
      post("/uploads/" + id + "/download-tickets", actor, Map.of())
          .andExpect(status().isNotFound());
    }
    URI signed = download(id);
    URI anonymous =
        new URI(signed.getScheme(), signed.getAuthority(), signed.getPath(), null, null);
    assertThat(
            http.send(
                    HttpRequest.newBuilder(anonymous).GET().build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode())
        .isEqualTo(403);
    long school =
        insert("INSERT INTO campus_school(name) VALUES(?)", "minio-live-" + UUID.randomUUID());
    var application =
        body(
            post(
                    "/merchant-applications",
                    owner,
                    Map.of(
                        "schoolId",
                        Long.toString(school),
                        "name",
                        "minio-live-store",
                        "category",
                        "DINING",
                        "address",
                        "minio-live-address",
                        "description",
                        "Real sealed qualification probe",
                        "qualificationFileId",
                        id))
                .andExpect(status().isCreated()));
    grant(admin, "merchant:review");
    var reviewTicket =
        body(
            post(
                    "/admin/merchant-applications/"
                        + application.path("id").asText()
                        + "/qualification-download-tickets",
                    admin,
                    Map.of())
                .andExpect(status().isOk()));
    assertThat(bytes(URI.create(reviewTicket.path("url").asText()))).isEqualTo(original);
  }

  @Test
  void signatureRejectsWrongSizeTypeAndExpiredUpload() throws Exception {
    byte[] data = "%PDF-1.7\ntest".getBytes(StandardCharsets.US_ASCII);
    var ticket = ticket("application/pdf", data.length, "MERCHANT_QUALIFICATION");
    var upload = URI.create(ticket.path("uploadUrl").asText());
    assertThat(putBytes(upload, "image/png", data)).isEqualTo(403);
    assertThat(putBytes(upload, "application/pdf", Arrays.copyOf(data, data.length - 1)))
        .isEqualTo(403);
    post(
            "/uploads",
            owner,
            Map.of(
                "fileName",
                "too-big.pdf",
                "contentType",
                "application/pdf",
                "size",
                257,
                "purpose",
                "MERCHANT_QUALIFICATION"))
        .andExpect(status().isBadRequest());
    String key = files.objectPrefix() + "/pending/minio-expiry-" + UUID.randomUUID();
    extraKeys.add(key);
    URI expired =
        storage.createUploadUrl(key, "application/pdf", data.length, Duration.ofSeconds(1));
    Thread.sleep(2100);
    assertThat(putBytes(expired, "application/pdf", data)).isEqualTo(403);
  }

  @Test
  void invalidMagicNeverBecomesReadyAndItsUnverifiedCopyIsRemoved() throws Exception {
    byte[] data = "not-a-png".getBytes(StandardCharsets.US_ASCII);
    var ticket = ticket("image/png", data.length, "COMMUNITY_IMAGE");
    String id = ticket.path("id").asText();
    assertThat(putBytes(URI.create(ticket.path("uploadUrl").asText()), "image/png", data))
        .isEqualTo(200);
    complete(id, owner).andExpect(status().isUnprocessableEntity());
    post("/uploads/" + id + "/download-tickets", owner, Map.of()).andExpect(status().isConflict());
    String key =
        jdbc.queryForObject(
            "SELECT immutable_key FROM campus_file_object WHERE id=?", String.class, id);
    URI removed = storage.createDownloadUrl(key, Duration.ofMinutes(1));
    assertThat(
            http.send(
                    HttpRequest.newBuilder(removed).GET().build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void browserCanUploadAndReadARealPrivateCommunityImageAcrossCors() throws Exception {
    byte[] png =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aIh0AAAAASUVORK5CYII=");
    var ticket = ticket("image/png", png.length, "COMMUNITY_IMAGE");
    String id = ticket.path("id").asText();
    var upload =
        browser(
            Map.of(
                "action",
                "upload",
                "url",
                ticket.path("uploadUrl").asText(),
                "contentType",
                "image/png",
                "payloadBase64",
                Base64.getEncoder().encodeToString(png)));
    assertThat(upload.path("status").asInt()).isEqualTo(200);
    complete(id, owner).andExpect(status().isOk());
    var downloaded = browser(Map.of("action", "download", "url", download(id).toString()));
    assertThat(downloaded.path("status").asInt()).isEqualTo(200);
    assertThat(Base64.getDecoder().decode(downloaded.path("bodyBase64").asText())).isEqualTo(png);
  }

  @Test
  void communityImageDownloadFollowsPostVisibilityWithoutExposingThePrivateFileApi()
      throws Exception {
    byte[] png =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jK0sAAAAASUVORK5CYII=");
    var upload = ticket("image/png", png.length, "COMMUNITY_IMAGE");
    String file = upload.path("id").asText();
    assertThat(putBytes(URI.create(upload.path("uploadUrl").asText()), "image/png", png))
        .isEqualTo(200);
    complete(file, owner).andExpect(status().isOk());
    long school =
        insert("INSERT INTO campus_school(name) VALUES(?)", "minio-live-" + UUID.randomUUID());
    var draft =
        body(
            post(
                    "/posts",
                    owner,
                    Map.of(
                        "schoolId",
                        Long.toString(school),
                        "content",
                        Map.of(
                            "title",
                            "minio-live image probe",
                            "body",
                            "Private community image visibility probe",
                            "imageIds",
                            List.of(file))))
                .andExpect(status().isCreated()));
    String postId = draft.path("id").asText(),
        images = "/posts/" + postId + "/image-download-tickets";
    post(images, other, Map.of()).andExpect(status().isNotFound());
    var ownImages = body(post(images, owner, Map.of()).andExpect(status().isOk()));
    assertThat(bytes(URI.create(ownImages.get(0).path("url").asText()))).isEqualTo(png);
    var review =
        body(
            post(
                    "/posts/" + postId + "/review-requests",
                    owner,
                    Map.of("version", draft.path("version").asInt()))
                .andExpect(status().isCreated()));
    grant(admin, "community:moderate");
    post(
            "/admin/post-review-requests/" + review.path("id").asText() + "/reviews",
            admin,
            Map.of("decision", "APPROVED", "reason", "minio-live approval"))
        .andExpect(status().isCreated());
    var publishedImages = body(post(images, other, Map.of()).andExpect(status().isOk()));
    assertThat(bytes(URI.create(publishedImages.get(0).path("url").asText()))).isEqualTo(png);
    post("/uploads/" + file + "/download-tickets", other, Map.of())
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", token(owner)))
        .andExpect(status().isNoContent());
    post(images, other, Map.of()).andExpect(status().isNotFound());
  }

  private JsonNode browser(Map<String, Object> request) throws Exception {
    Path evidence = Path.of(System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"));
    Path root = Path.of(System.getenv("CAMPUS_PROJECT_ROOT"));
    Path input = evidence.resolve("minio-browser-" + UUID.randomUUID() + ".json");
    Path output = evidence.resolve("minio-browser-result-" + UUID.randomUUID() + ".json");
    Process process = null;
    try {
      Files.write(input, json.writeValueAsBytes(request));
      process =
          new ProcessBuilder(
                  "node",
                  root.resolve("scripts/minio-live-browser-check.mjs").toString(),
                  input.toString())
              .redirectError(output.resolveSibling(output.getFileName() + ".err").toFile())
              .redirectOutput(output.toFile())
              .start();
      assertThat(process.waitFor(50, TimeUnit.SECONDS))
          .as("Storage browser probe must finish")
          .isTrue();
      assertThat(process.exitValue()).as("See private browser probe diagnostics").isZero();
      return json.readTree(Files.readAllBytes(output));
    } finally {
      if (process != null && process.isAlive()) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
      }
      Files.deleteIfExists(input);
    }
  }
}
