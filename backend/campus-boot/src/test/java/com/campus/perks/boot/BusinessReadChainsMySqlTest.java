package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.*;
import com.campus.perks.notification.serviceImpl.NotificationInbox;
import com.campus.perks.trade.serviceImpl.OrderServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Missing query links: real controllers/services/MySQL; storage/payment are explicit test doubles.
 * Run only business* methods; inherited tests remain covered by the original class.
 */
@EnabledIfEnvironmentVariables({
  @EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true"),
  @EnabledIfEnvironmentVariable(named = "CAMPUS_BUSINESS_EVIDENCE_DIR", matches = ".+")
})
class BusinessReadChainsMySqlTest extends RbacIsolationMySqlTest {
  @MockitoBean ObjectStoragePort storage;
  @MockitoBean SandboxPaymentPort payment;
  @Autowired OrderServiceImpl orders;
  @Autowired NotificationInbox inbox;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  long school, store, unrelatedMerchant, unrelatedStore, restrictedAdmin;
  String file;

  @BeforeEach
  void businessFixtures() {
    restrictedAdmin = account("ADMIN");
    db.update(
        "INSERT IGNORE INTO campus_role_permission(role_id,permission_id) SELECT ?,id FROM"
            + " campus_permission",
        role);
    school =
        insert("INSERT INTO campus_school(name) VALUES(?)", "business-test-" + UUID.randomUUID());
    file = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at,completed_at)"
            + " VALUES(?,?,'MERCHANT_QUALIFICATION','fixture.pdf','application/pdf',64,?,?,'READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
            + " 1 HOUR),UTC_TIMESTAMP(3))",
        file,
        merchant,
        "business/pending/" + file,
        "business/sealed/" + file);
    store = store(merchant);
    unrelatedMerchant = account("MERCHANT");
    unrelatedStore = store(unrelatedMerchant);
    when(storage.available()).thenReturn(true);
    when(storage.createDownloadUrl(anyString(), any()))
        .thenReturn(URI.create("https://storage.invalid/private?signature=fixture"));
    when(payment.available()).thenReturn(true);
    when(payment.checkout(anyString(), any(), anyString(), any()))
        .thenReturn("https://openapi.alipaydev.com/gateway.do?fixture=1");
  }

  private long store(long owner) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_merchant(owner_id,school_id,name,category,address,status,qualification_file_id)"
                + " VALUES(?,?,'business-test-store','DINING','fixture','APPROVED',?)",
            owner,
            school,
            owner == merchant ? file : null);
    db.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'OWNER')",
        id,
        owner);
    return id;
  }

  private ResultActions request(String method, String path, long actor, Object input)
      throws Exception {
    return call(method, path, actor == 0 ? "" : token(actor), input);
  }

  private JsonNode read(String path, long actor) throws Exception {
    return body(request("GET", path, actor, null).andExpect(status().isOk()));
  }

  private JsonNode create(String path, long actor, Object input) throws Exception {
    return body(request("POST", path, actor, input).andExpect(status().isCreated()));
  }

  private JsonNode keyedCreate(String path, long actor, Object input) throws Exception {
    return body(
        mvc.perform(
                post("/api/v1" + path)
                    .header("Authorization", token(actor))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(input)))
            .andExpect(status().isCreated()));
  }

  @Test
  void businessRbacQueriesAndAssignmentHaveRealPersistentResults() throws Exception {
    assertThat(read("/admin/permissions", admin).isArray()).isTrue();
    assertThat(read("/admin/roles", admin).path("total").asLong()).isPositive();
    assertThat(read("/admin/roles/" + role, admin).path("id").asText()).isEqualTo("" + role);
    assertThat(read("/admin/accounts", admin).path("total").asLong()).isPositive();
    assertThat(read("/admin/accounts/" + user, admin).has("phone")).isFalse();
    long target = account("ADMIN");
    request(
            "PUT",
            "/admin/accounts/" + target + "/roles",
            admin,
            Map.of("version", 0, "roleIds", List.of("" + role)))
        .andExpect(status().isOk());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_user_role WHERE user_id=? AND role_id=?",
                Integer.class,
                target,
                role))
        .isOne();
    assertThat(read("/admin/accounts/" + target, admin).path("roleIds").toString())
        .contains("" + role);
    request("GET", "/admin/roles/" + role, user, null).andExpect(status().isForbidden());
    request("GET", "/admin/accounts/" + target, merchant, null).andExpect(status().isForbidden());
    for (String path :
        List.of(
            "/admin/permissions", "/admin/roles", "/admin/accounts", "/admin/accounts/" + target))
      request("GET", path, user, null).andExpect(status().isForbidden());
  }

  @Test
  void businessMerchantFileAndCampusQueriesRespectOwnership() throws Exception {
    assertThat(read("/merchant-categories", 0).isArray()).isTrue();
    assertThat(read("/schools", 0).isArray()).isTrue();
    assertThat(read("/users/me/merchants", merchant).path("total").asLong()).isOne();
    assertThat(read("/merchants/" + store + "/members", merchant).path("total").asLong()).isOne();
    assertThat(read("/admin/merchants/" + store, admin).path("id").asText()).isEqualTo("" + store);
    request("POST", "/merchants/" + store + "/qualification-download-tickets", merchant, Map.of())
        .andExpect(status().is2xxSuccessful());
    request("GET", "/merchants/" + store + "/members", unrelatedMerchant, null)
        .andExpect(status().isNotFound());
    request(
            "POST",
            "/merchants/" + store + "/qualification-download-tickets",
            unrelatedMerchant,
            Map.of())
        .andExpect(status().isNotFound());
    assertThat(read("/uploads", merchant).path("total").asLong()).isOne();
    assertThat(read("/uploads/" + file, merchant).path("id").asText()).isEqualTo(file);
    request("GET", "/uploads/" + file, user, null).andExpect(status().isNotFound());
    assertThat(read("/users/me/merchant-applications", user).path("total").asLong()).isZero();
    var verification =
        create(
            "/users/me/campus-verifications",
            user,
            Map.of("schoolId", "" + school, "realName", "测试同学", "studentNumber", "TEST-2026"));
    assertThat(
            read("/users/me/campus-verifications/" + verification.path("id").asText(), user)
                .path("id")
                .asText())
        .isEqualTo(verification.path("id").asText());
    assertThat(read("/sessions/current", user).path("userId").asText()).isEqualTo("" + user);
    request(
            "GET",
            "/users/me/campus-verifications/" + verification.path("id").asText(),
            account("USER"),
            null)
        .andExpect(status().isNotFound());
  }

  private JsonNode coupon() throws Exception {
    var content =
        Map.of(
            "title",
            "business-test-coupon",
            "description",
            "fixture",
            "couponType",
            "AMOUNT_OFF",
            "rules",
            Map.of("threshold", 50, "amountOff", 10),
            "totalStock",
            5,
            "validFrom",
            Instant.now().minusSeconds(60),
            "validUntil",
            Instant.now().plusSeconds(3600),
            "campusOnly",
            false);
    var draft = create("/merchants/" + store + "/coupons", merchant, content);
    String id = draft.path("id").asText();
    var review =
        create(
            "/coupons/" + id + "/review-requests",
            merchant,
            Map.of("version", draft.path("version").asInt()));
    create(
        "/admin/coupon-review-requests/" + review.path("id").asText() + "/reviews",
        admin,
        Map.of("decision", "APPROVED", "reason", "fixture approval"));
    var approved = read("/coupons/" + id + "/management", merchant);
    var change =
        create(
            "/coupons/" + id + "/status-changes",
            merchant,
            Map.of(
                "version",
                approved.path("version").asInt(),
                "status",
                "PUBLISHED",
                "reason",
                "fixture publication"));
    assertThat(
            read("/coupons/" + id + "/status-changes/" + change.path("id").asText(), merchant)
                .path("id")
                .asText())
        .isEqualTo(change.path("id").asText());
    return read("/coupons/" + id, 0);
  }

  @Test
  void businessCouponReadChainIncludesManagementReviewClaimsAndRedemptionHistory()
      throws Exception {
    var published = coupon();
    assertThat(read("/admin/coupon-review-requests", admin).path("total").asLong()).isPositive();
    assertThat(read("/merchants/" + store + "/coupons", merchant).path("total").asLong()).isOne();
    var claim = create("/coupons/" + published.path("id").asText() + "/claims", user, Map.of());
    assertThat(read("/users/me/coupon-claims", user).path("items").toString())
        .contains(claim.path("id").asText());
    assertThat(read("/merchants/" + store + "/redemptions", merchant).path("total").asLong())
        .isZero();
    request("GET", "/merchants/" + store + "/coupons", unrelatedMerchant, null)
        .andExpect(status().isNotFound());
    request("GET", "/merchants/" + store + "/redemptions", unrelatedMerchant, null)
        .andExpect(status().isNotFound());
  }

  private String offer(String kind) throws Exception {
    var draft =
        create(
            "/merchants/" + store + "/trade-offers",
            merchant,
            Map.of(
                "title",
                "business-test-meal",
                "description",
                "fixture",
                "kind",
                kind,
                "price",
                "19.90",
                "stock",
                4,
                "startsAt",
                Instant.now().minusSeconds(60),
                "endsAt",
                Instant.now().plusSeconds(3600),
                "groupSize",
                kind.equals("GROUP") ? 2 : 1,
                "groupDurationSeconds",
                kind.equals("GROUP") ? 600 : 0));
    String id = draft.path("id").asText();
    request("PATCH", "/trade-offers/" + id, merchant, Map.of("status", "PUBLISHED", "version", 0))
        .andExpect(status().isOk());
    return id;
  }

  @Test
  void businessTradeQueriesPreserveSnapshotsAndSeparateBuyerAndStoreViews() throws Exception {
    String offer = offer("REGULAR");
    assertThat(read("/trade-offers", 0).path("total").asLong()).isPositive();
    assertThat(read("/trade-offers/" + offer, 0).path("id").asText()).isEqualTo(offer);
    assertThat(read("/merchants/" + store + "/trade-offers", merchant).path("total").asLong())
        .isOne();
    assertThat(read("/merchants/" + store + "/trade-offers/" + offer, merchant).path("id").asText())
        .isEqualTo(offer);
    var order = keyedCreate("/orders", user, Map.of("offerId", offer));
    String id = order.path("id").asText();
    assertThat(read("/orders", user).path("total").asLong()).isOne();
    assertThat(read("/orders/" + id, user).path("amount").asText()).isEqualTo("19.90");
    assertThat(read("/merchants/" + store + "/orders", merchant).path("total").asLong()).isOne();
    var storeView = read("/merchants/" + store + "/orders/" + id, merchant);
    assertThat(storeView.has("phone") || storeView.has("userId")).isFalse();
    var attempt = create("/orders/" + id + "/payments", user, Map.of());
    assertThat(
            read("/orders/" + id + "/payments/" + attempt.path("id").asText(), user)
                .hasNonNull("checkoutUrl"))
        .isFalse();
    orders.accept(
        new SandboxPaymentPort.VerifiedPayment(
            UUID.randomUUID().toString(),
            order.path("orderNo").asText(),
            "T" + id,
            new BigDecimal("19.90"),
            "TRADE_SUCCESS"),
        "fixture-hash");
    var refund = create("/orders/" + id + "/refunds", user, Map.of());
    read("/orders/" + id + "/refunds", user);
    assertThat(
            read("/orders/" + id + "/refunds/" + refund.path("id").asText(), user)
                .path("id")
                .asText())
        .isEqualTo(refund.path("id").asText());
    request("GET", "/merchants/" + store + "/orders/" + id, unrelatedMerchant, null)
        .andExpect(status().isNotFound());
    assertThat(
            db.queryForObject("SELECT amount FROM campus_order WHERE id=?", BigDecimal.class, id))
        .isEqualByComparingTo("19.90");
    long stranger = account("USER");
    for (String path :
        List.of(
            "/orders/" + id,
            "/orders/" + id + "/payments/" + attempt.path("id").asText(),
            "/orders/" + id + "/refunds",
            "/orders/" + id + "/refunds/" + refund.path("id").asText()))
      request("GET", path, stranger, null).andExpect(status().isNotFound());
    for (String path :
        List.of(
            "/merchants/" + store + "/trade-offers/" + offer, "/merchants/" + store + "/orders"))
      request("GET", path, unrelatedMerchant, null).andExpect(status().isNotFound());
  }

  @Test
  void businessGroupProgressDoesNotExposeMemberIdentities() throws Exception {
    String offer = offer("GROUP");
    var order = keyedCreate("/orders", user, Map.of("offerId", offer));
    String group = order.path("groupId").asText();
    assertThat(group).isNotBlank();
    var progress = read("/group-purchases/" + group, user);
    assertThat(progress.has("members") || progress.has("userIds")).isFalse();
    assertThat(read("/trade-offers/" + offer + "/group-purchases", 0).path("total").asLong())
        .isPositive();
  }

  @Test
  void businessCommunityQueuesAndOwnQueriesRespectReviewVisibility() throws Exception {
    var draft =
        create(
            "/posts",
            user,
            Map.of(
                "schoolId",
                "" + school,
                "merchantId",
                "" + store,
                "content",
                Map.of("title", "business-test-visit", "body", "校园探店测试", "imageIds", List.of())));
    String id = draft.path("id").asText();
    assertThat(read("/users/me/posts", user).path("total").asLong()).isOne();
    read("/users/me/posts/" + id, user);
    request("GET", "/users/me/posts/" + id, account("USER"), null).andExpect(status().isNotFound());
    var review =
        create(
            "/posts/" + id + "/review-requests",
            user,
            Map.of("version", draft.path("version").asInt()));
    assertThat(read("/admin/post-review-requests", admin).path("total").asLong()).isPositive();
    create(
        "/admin/post-review-requests/" + review.path("id").asText() + "/reviews",
        admin,
        Map.of("decision", "APPROVED", "reason", "fixture approval"));
    assertThat(read("/posts", 0).path("total").asLong()).isPositive();
    assertThat(read("/posts/" + id + "/likes/me", user).path("liked").asBoolean()).isFalse();
    var comment = keyedCreate("/posts/" + id + "/comments", user, Map.of("body", "测试评论"));
    assertThat(read("/post-comments/" + comment.path("id").asText(), user).path("id").asText())
        .isEqualTo(comment.path("id").asText());
    request("GET", "/post-comments/" + comment.path("id").asText(), account("USER"), null)
        .andExpect(status().isNotFound());
    assertThat(read("/users/me/post-comments", user).path("total").asLong()).isOne();
    assertThat(read("/admin/post-comments", admin).path("total").asLong()).isPositive();
    var report = create("/posts/" + id + "/reports", user, Map.of("reason", "测试举报"));
    assertThat(read("/post-reports/" + report.path("id").asText(), user).path("id").asText())
        .isEqualTo(report.path("id").asText());
    assertThat(read("/admin/post-reports", admin).path("total").asLong()).isPositive();
  }

  @Test
  void businessNotificationAndDeliveryQueriesExposeMetadataWithoutRawPayloads() throws Exception {
    String event = UUID.randomUUID().toString();
    var notification =
        inbox.deliver(
            new EventPublisherPort.Event(
                event,
                "ORDER_CREATED",
                "1",
                Instant.now(),
                Map.of("userId", "" + user, "orderId", "1", "merchantId", "" + store)));
    assertThat(read("/users/me/notifications/" + notification.id(), user).path("id").asText())
        .isEqualTo(notification.id());
    request("GET", "/users/me/notifications/" + notification.id(), account("USER"), null)
        .andExpect(status().isNotFound());
    db.update(
        "INSERT INTO campus_outbox_event(id,aggregate_id,event_type,payload)"
            + " VALUES(?,?,'ORDER_CREATED',JSON_OBJECT('userId',?))",
        event,
        "1",
        "" + user);
    assertThat(read("/admin/notification-deliveries?status=PENDING", admin).path("total").asLong())
        .isPositive();
    assertThat(read("/admin/notification-deliveries/" + event, admin).has("payload")).isFalse();
    request("GET", "/admin/notification-deliveries/" + event, user, null)
        .andExpect(status().isForbidden());
    read("/notification-capabilities", user);
  }

  @Test
  void businessCapabilitiesLocationAndRebuildRecoveryHaveQueryCoverage() throws Exception {
    read("/map-capabilities", 0);
    read("/search-capabilities", 0);
    read("/trade-capabilities", 0);
    request(
            "PUT",
            "/merchants/" + store + "/location",
            merchant,
            Map.of(
                "longitude",
                "116.4",
                "latitude",
                "39.9",
                "coordinateSystem",
                "WGS84",
                "version",
                0))
        .andExpect(status().isOk());
    read("/merchants/" + store + "/location", merchant);
    var job = create("/admin/search-index-rebuilds", admin, null);
    String id = job.path("id").asText();
    assertThat(read("/admin/search-index-rebuilds/" + id, admin).path("id").asText()).isEqualTo(id);
    assertThat(read("/admin/search-index-rebuilds", admin).path("total").asLong()).isPositive();
    db.update(
        "UPDATE campus_search_rebuild SET status='FAILED',error_code='TEST_FAILURE' WHERE id=? AND"
            + " requested_by=?",
        id,
        admin);
    request("POST", "/admin/search-index-rebuilds/" + id + "/retries", admin, null)
        .andExpect(status().isAccepted());
    assertThat(read("/admin/search-index-rebuilds/" + id, admin).path("status").asText())
        .isEqualTo("PENDING");
    assertThat(
            db.queryForObject(
                "SELECT requested_by FROM campus_search_rebuild WHERE id=?", Long.class, id))
        .isEqualTo(admin);
  }

  @Test
  void businessReadyCommunityImagesReturnScopedDownloadTickets() throws Exception {
    String image = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at,completed_at)"
            + " VALUES(?,?,'COMMUNITY_IMAGE','fixture.png','image/png',64,?,?,'READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL"
            + " 1 HOUR),UTC_TIMESTAMP(3))",
        image,
        user,
        "business/pending/" + image,
        "business/sealed/" + image);
    var draft =
        create(
            "/posts",
            user,
            Map.of(
                "schoolId",
                "" + school,
                "merchantId",
                "" + store,
                "content",
                Map.of(
                    "title", "business-test-image", "body", "校园图片测试", "imageIds", List.of(image))));
    var tickets =
        body(
            request(
                    "POST",
                    "/posts/" + draft.path("id").asText() + "/image-download-tickets",
                    user,
                    null)
                .andExpect(status().isOk()));
    assertThat(tickets.isArray()).isTrue();
    assertThat(tickets.size()).isOne();
    request(
            "POST",
            "/posts/" + draft.path("id").asText() + "/image-download-tickets",
            unrelatedMerchant,
            null)
        .andExpect(status().isNotFound());
  }

  @Test
  void businessUnconfiguredMapProxyFailsExplicitlyWithoutProviderCalls() throws Exception {
    request("GET", "/maps/_AMapService/v4/unauthorized/resource", 0, null)
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void businessPlatformAdministratorUsesAllPortalsWithoutChangingIdentityOrStoreOwner()
      throws Exception {
    request(
            "PATCH",
            "/merchants/" + store,
            admin,
            Map.of(
                "version",
                0,
                "description",
                "platform test update",
                "businessHours",
                "09:00-18:00"))
        .andExpect(status().isOk());
    read("/merchants/" + store + "/coupons", admin);
    read("/merchants/" + store + "/marketing-drafts", admin);
    read("/merchants/" + store + "/dashboard", admin);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/merchant/dashboard")
                .param("merchantId", "" + store)
                .header("Authorization", token(admin)))
        .andExpect(status().isOk());
    read("/orders", admin);
    read("/admin/accounts", admin);
    read("/admin/campus-verifications", admin);
    read("/admin/merchant-applications", admin);
    assertThat(read("/users/me", admin).path("accountType").asText()).isEqualTo("ADMIN");
    assertThat(
            db.queryForObject("SELECT owner_id FROM campus_merchant WHERE id=?", Long.class, store))
        .isEqualTo(merchant);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM campus_merchant_member WHERE user_id=?",
                Integer.class,
                admin))
        .isZero();
  }

  private String portal(String path, String method) {
    if (path.startsWith("/api/v1/admin/")) return "ADMIN";
    if (path.startsWith("/api/v1/merchants/")
            && (path.split("/").length > 5 || method.equals("PATCH"))
        || path.equals("/api/v1/users/me/merchants")
        || path.startsWith("/api/v1/redemptions")
        || path.startsWith("/api/v1/merchant/")
        || path.startsWith("/api/v1/coupons/")
            && !path.endsWith("/claims")
            && (method.equals("PATCH")
                || path.contains("/management")
                || path.contains("/review-requests")
                || path.contains("/status-changes"))
        || path.startsWith("/api/v1/trade-offers/") && method.equals("PATCH")) return "MERCHANT";
    if (path.startsWith("/api/v1/orders")
        || path.startsWith("/api/v1/coupon-claims/")
        || path.startsWith("/api/v1/group-purchases/")
        || path.startsWith("/api/v1/merchant-applications")
        || path.matches(
            "/api/v1/users/me/(posts|post-comments|coupon-claims|campus-verifications|merchant-applications)(/.*)?")
        || path.startsWith("/api/v1/coupons/") && path.endsWith("/claims")
        || path.startsWith("/api/v1/posts")
            && (List.of("POST", "PATCH", "DELETE").contains(method)
                    && !path.endsWith("/image-download-tickets")
                || path.endsWith("/likes/me"))
        || path.startsWith("/api/v1/post-comments/") && method.equals("DELETE")) return "USER";
    return null;
  }

  @Test
  void businessEveryPortalBoundOperationRejectsBothOtherPortals() throws Exception {
    // A platform business administrator is an authorized cross-portal principal, tested separately.
    Map<String, Long> actors = Map.of("USER", user, "MERCHANT", merchant, "ADMIN", restrictedAdmin);
    var results = new ArrayList<Map<String, Object>>();
    var failures = new ArrayList<String>();
    for (var entry : mappings.getHandlerMethods().entrySet()) {
      String type = entry.getValue().getBeanType().getName();
      if (!type.startsWith("com.campus.perks.") || type.contains(".assistant.")) continue;
      for (String path : entry.getKey().getPatternValues()) {
        for (var method : entry.getKey().getMethodsCondition().getMethods()) {
          String required = portal(path, method.name());
          if (required == null) continue;
          for (var actor : actors.entrySet()) {
            if (actor.getKey().equals(required)) continue;
            int status =
                request(
                        method.name(),
                        path.replace("/api/v1", "").replaceAll("\\{[^}]+}", "1"),
                        actor.getValue(),
                        List.of("POST", "PUT", "PATCH").contains(method.name()) ? Map.of() : null)
                    .andReturn()
                    .getResponse()
                    .getStatus();
            results.add(
                Map.of(
                    "method",
                    method.name(),
                    "path",
                    path,
                    "portal",
                    actor.getKey(),
                    "required",
                    required,
                    "status",
                    status));
            if (status != 403 && status != 404)
              failures.add(method + " " + path + " / " + actor.getKey() + " -> " + status);
          }
        }
      }
    }
    json.writerWithDefaultPrettyPrinter()
        .writeValue(
            Path.of(System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"), "cross-portal-sweep.json")
                .toFile(),
            results);
    assertThat(results).isNotEmpty();
    assertThat(failures)
        .as("Wrong portal must be denied before a successful business mutation")
        .isEmpty();
  }

  @Test
  void businessEveryJsonBodyRejectsMalformedJsonBeforeCallingItsService() throws Exception {
    var results = new ArrayList<Map<String, Object>>();
    var failures = new ArrayList<String>();
    for (var entry : mappings.getHandlerMethods().entrySet()) {
      String type = entry.getValue().getBeanType().getName();
      if (!type.startsWith("com.campus.perks.")
          || type.contains(".assistant.")
          || Arrays.stream(entry.getValue().getMethodParameters())
              .noneMatch(parameter -> parameter.hasParameterAnnotation(RequestBody.class)))
        continue;
      for (String path : entry.getKey().getPatternValues()) {
        for (var method : entry.getKey().getMethodsCondition().getMethods()) {
          String required = portal(path, method.name());
          long actor =
              "MERCHANT".equals(required) ? merchant : "ADMIN".equals(required) ? admin : user;
          int status =
              mvc.perform(
                      org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                              org.springframework.http.HttpMethod.valueOf(method.name()),
                              path.replaceAll("\\{[^}]+}", "1"))
                          .header("Authorization", token(actor))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{malformed"))
                  .andReturn()
                  .getResponse()
                  .getStatus();
          results.add(Map.of("method", method.name(), "path", path, "status", status));
          if (status != 400) failures.add(method + " " + path + " -> " + status);
        }
      }
    }
    json.writerWithDefaultPrettyPrinter()
        .writeValue(
            Path.of(System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"), "malformed-json-sweep.json")
                .toFile(),
            results);
    assertThat(results).isNotEmpty();
    assertThat(failures).as("Malformed JSON must fail before the business service").isEmpty();
  }

  @Test
  void businessEveryBearerOperationRejectsAnonymousAndInvalidTokensWithLiveSecurity()
      throws Exception {
    var results = new ArrayList<Map<String, Object>>();
    var failures = new ArrayList<String>();
    for (var entry : mappings.getHandlerMethods().entrySet()) {
      var handler = entry.getValue();
      if (!handler.getBeanType().getName().startsWith("com.campus.perks.")
          || handler.getBeanType().getName().contains(".assistant.")) continue;
      boolean secured =
          java.util.stream.Stream.concat(
                  Arrays.stream(
                      handler.getBeanType().getAnnotationsByType(SecurityRequirement.class)),
                  Arrays.stream(
                      handler.getMethod().getAnnotationsByType(SecurityRequirement.class)))
              .anyMatch(annotation -> annotation.name().equals("bearerAuth"));
      if (!secured) continue;
      for (String path : entry.getKey().getPatternValues()) {
        for (var method : entry.getKey().getMethodsCondition().getMethods()) {
          for (boolean malformedToken : List.of(false, true)) {
            var input =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                        org.springframework.http.HttpMethod.valueOf(method.name()),
                        path.replaceAll("\\{[^}]+}", "1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}");
            if (malformedToken) input.header("Authorization", "Bearer invalid-test-token");
            int status = mvc.perform(input).andReturn().getResponse().getStatus();
            results.add(
                Map.of(
                    "method",
                    method.name(),
                    "path",
                    path,
                    "invalidToken",
                    malformedToken,
                    "status",
                    status));
            if (status != 401)
              failures.add(
                  method + " " + path + " / invalidToken=" + malformedToken + " -> " + status);
          }
        }
      }
    }
    json.writerWithDefaultPrettyPrinter()
        .writeValue(
            Path.of(
                    System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"),
                    "mysql-authentication-sweep.json")
                .toFile(),
            results);
    assertThat(results).isNotEmpty();
    assertThat(failures).as("JWT enabled, real DB: no anonymous or invalid-token bypass").isEmpty();
  }
}
