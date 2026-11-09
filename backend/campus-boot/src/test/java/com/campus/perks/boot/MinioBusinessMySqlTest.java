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
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "campus.auth.enabled=true",
      "campus.security.jwt.enabled=true",
      "campus.security.allowed-origins=http://127.0.0.1:5173",
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
  @Autowired com.campus.perks.user.serviceImpl.CampusEvidenceRetentionService retention;
  @Autowired javax.sql.DataSource dataSource;
  @Autowired com.campus.perks.user.service.AuthSecrets authSecrets;
  @org.springframework.boot.test.web.server.LocalServerPort int backendPort;
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

  @Test
  void studentCardActualBytesArePrivateAndRemovedAfterReviewedRetention() throws Exception {
    byte[] png=syntheticPng();
    var upload=ticket("image/png",png.length,"STUDENT_CARD");
    String file=upload.path("id").asText();
    assertThat(putBytes(URI.create(upload.path("uploadUrl").asText()),"image/png",png)).isEqualTo(200);
    complete(file,owner).andExpect(status().isOk());
    long school=insert("INSERT INTO campus_school(name) VALUES(?)","minio-card-"+UUID.randomUUID());
    String application=body(post("/users/me/campus-verifications",owner,
      Map.of("schoolId",""+school,"realName","合成测试同学","studentNumber","SYNTHETIC-CARD","studentCardFileId",file))
      .andExpect(status().isCreated())).path("id").asText();
    grant(admin,"campus:review");
    String route="/admin/campus-verifications/"+application+"/student-card-download-tickets";
    post(route,other,Map.of()).andExpect(status().isForbidden());
    post("/users/me/campus-verifications/"+application+"/student-card-download-tickets",other,Map.of()).andExpect(status().isNotFound());
    URI signed=URI.create(body(post(route,admin,Map.of()).andExpect(status().isOk())).path("url").asText());
    assertThat(bytes(signed)).isEqualTo(png);
    URI unsigned=new URI(signed.getScheme(),signed.getAuthority(),signed.getPath(),null,null);
    assertThat(http.send(HttpRequest.newBuilder(unsigned).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
    assertThat(retention.purge(application)).isFalse();
    post("/admin/campus-verifications/"+application+"/reviews",admin,Map.of("decision","APPROVED","reason","合成照片流程验证")).andExpect(status().isCreated());
    jdbc.update("UPDATE campus_campus_verification SET reviewed_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 31 DAY) WHERE id=?",application);
    assertThat(retention.purge(application)).isTrue();
    assertThat(http.send(HttpRequest.newBuilder(signed).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
    post(route,admin,Map.of()).andExpect(status().isGone());
    assertThat(jdbc.queryForObject("SELECT school_id FROM campus_user WHERE id=?",Long.class,owner)).isEqualTo(school);
  }

  @Test
  void actualAvatarAndDefaultAssetsAreCloudObjectsWithScopedBinding() throws Exception {
    byte[] png=syntheticPng();
    var upload=ticket("image/png",png.length,"USER_AVATAR");String file=upload.path("id").asText();
    assertThat(putBytes(URI.create(upload.path("uploadUrl").asText()),"image/png",png)).isEqualTo(200);
    complete(file,owner).andExpect(status().isOk());
    mvc.perform(put("/api/v1/users/me/avatar").header("Authorization",token(other)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("fileId",file)))).andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/users/me/avatar").header("Authorization",token(owner)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("fileId",file)))).andExpect(status().isOk());
    var redirect=mvc.perform(get("/api/v1/users/"+owner+"/avatar")).andExpect(status().isFound()).andReturn().getResponse();
    assertThat(bytes(URI.create(redirect.getHeader("Location")))).isEqualTo(png);
    post("/uploads/"+file+"/download-tickets",other,Map.of()).andExpect(status().isNotFound());
    for(String key:List.of("campus-blue","sunshine","fresh-green","star-purple")){
      var image=mvc.perform(get("/api/v1/default-avatars/"+key+"/image")).andExpect(status().isFound()).andReturn().getResponse();
      byte[] actual=bytes(URI.create(image.getHeader("Location")));
      assertThat(actual.length).isGreaterThan(100);
      assertThat(Arrays.copyOf(actual,8)).isEqualTo(Arrays.copyOf(png,8));
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named="CAMPUS_MEDIA_BROWSER_TEST",matches="true")
  @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void realVueStudentCardAndAvatarJourneyUsesActualMinio() throws Exception {
    long school=0;Process process=null;
    var roots=List.of(owner,other,merchant,admin);
    var roles=new ArrayList<Long>();
    try {
      school=insert("INSERT INTO campus_school(name) VALUES(?)","synthetic-media-"+UUID.randomUUID());
      grant(admin,"campus:review");
      roles.addAll(jdbc.queryForList("SELECT role_id FROM campus_user_role WHERE user_id=?",Long.class,admin));
      String password=UUID.randomUUID()+"aA7!";
      String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(password);
      for(long actor:new long[]{owner,admin})jdbc.update("UPDATE campus_user SET password_hash=? WHERE id=?",hash,actor);
      Path root=Path.of(System.getenv("CAMPUS_FRONTEND_PROJECT_ROOT"));
      Path evidence=Path.of(System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"));
      var command=new ProcessBuilder("node",root.resolve("frontend/scripts/live-media-browser-check.mjs").toString())
        .directory(root.toFile()).redirectErrorStream(true).redirectOutput(evidence.resolve("media-browser-live.log").toFile());
      var environment=command.environment();
      environment.put("CAMPUS_MEDIA_BACKEND_PORT",""+backendPort);
      environment.put("CAMPUS_MEDIA_USER_PHONE",jdbc.queryForObject("SELECT phone FROM campus_user WHERE id=?",String.class,owner));
      environment.put("CAMPUS_MEDIA_ADMIN_PHONE",jdbc.queryForObject("SELECT phone FROM campus_user WHERE id=?",String.class,admin));
      environment.put("CAMPUS_MEDIA_PASSWORD",password);
      environment.put("CAMPUS_MEDIA_PNG_BASE64",Base64.getEncoder().encodeToString(syntheticPng()));
      environment.put("CAMPUS_MEDIA_SCHOOL_ID",""+school);
      process=command.start();
      assertThat(process.waitFor(150,TimeUnit.SECONDS)).as("Real media browser journey timeout").isTrue();
      assertThat(process.exitValue()).as("Inspect private media-browser-live.log").isZero();
      assertThat(jdbc.queryForObject("SELECT status FROM campus_campus_verification WHERE user_id=?",String.class,owner)).isEqualTo("APPROVED");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campus_user_avatar WHERE user_id=? AND file_id IS NOT NULL",Integer.class,owner)).isEqualTo(1);
    } finally {
      if(process!=null && process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}
      for(long actor:roots){
        var files=jdbc.queryForList("SELECT staging_key,immutable_key FROM campus_file_object WHERE owner_id=?",actor);
        for(var keys:files){storage.delete(keys.get("staging_key").toString());storage.delete(keys.get("immutable_key").toString());}
        String phone=jdbc.queryForObject("SELECT phone FROM campus_user WHERE id=?",String.class,actor);
        jdbc.update("DELETE FROM campus_verification_rate WHERE rate_key=?",authSecrets.hash("rate:password-phone:"+phone));
        // Remove this exact fixture row before generic nullable-FK cleanup; avatar source has an XOR CHECK.
        jdbc.update("DELETE FROM campus_user_avatar WHERE user_id=?",actor);
      }
      var cleanupRoots=new LinkedHashMap<String,List<Long>>();
      cleanupRoots.put("campus_user",roots);if(school>0)cleanupRoots.put("campus_school",List.of(school));if(!roles.isEmpty())cleanupRoots.put("campus_role",roles);
      new com.campus.perks.boot.support.BusinessDatasetCleanup(dataSource,cleanupRoots).clean();
      jdbc.update("DELETE FROM campus_verification_rate WHERE rate_key=?",authSecrets.hash("rate:password-ip:127.0.0.1"));
    }
  }

  private byte[] syntheticPng() throws Exception {
    var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for(int x=0;x<2;x++)for(int y=0;y<2;y++)image.setRGB(x,y,0xff2454da);
    var output=new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image,"png",output);
    return output.toByteArray();
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
