package com.campus.perks.assistant.serviceImpl;

import com.campus.perks.assistant.service.AssistantProperties;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.AiModelPort.Tool;
import com.campus.perks.security.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Model-selected names map to fixed GET resources. No URLs, methods, credentials or actor IDs are
 * accepted.
 */
@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AssistantToolRegistry implements ApplicationListener<WebServerInitializedEvent> {
  private record Definition(Tool tool, AccountPortal portal, String permission, String path) {}

  private final List<Definition> definitions = new ArrayList<>();
  private final ObjectMapper json;
  private final AssistantProperties p;
  private final HttpClient http;
  private volatile int port;
  private final AssistantBusinessApiTools business;

  public AssistantToolRegistry(
      ObjectMapper json, AssistantProperties p, AssistantBusinessApiTools business) {
    this.business = business;
    this.json = json;
    this.p = p;
    http =
        HttpClient.newBuilder()
            .connectTimeout(p.toolTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    add(
        "search_coupons",
        "查询公开优惠，返回当前规则和有效期",
        AccountPortal.USER,
        null,
        "/coupons",
        Map.of("keyword", "优惠关键词"),
        List.of());
    add("my_orders", "查询当前登录用户本人订单", AccountPortal.USER, null, "/orders", Map.of(), List.of());
    add(
        "my_coupon_claims",
        "查询当前登录用户本人券包",
        AccountPortal.USER,
        null,
        "/users/me/coupon-claims",
        Map.of(),
        List.of());
    add(
        "my_stores",
        "查询当前商家账号所属门店及门店角色",
        AccountPortal.MERCHANT,
        null,
        "/users/me/merchants",
        Map.of(),
        List.of());
    add(
        "store_orders",
        "查询本人有权经营的指定门店订单",
        AccountPortal.MERCHANT,
        null,
        "/merchants/{merchantId}/orders",
        Map.of("merchantId", "从my_stores取得的门店ID"),
        List.of("merchantId"));
    add(
        "store_coupons",
        "查询本人有权经营的指定门店优惠",
        AccountPortal.MERCHANT,
        null,
        "/merchants/{merchantId}/coupons",
        Map.of("merchantId", "从my_stores取得的门店ID"),
        List.of("merchantId"));
    add(
        "pending_merchant_reviews",
        "查询待审核商家申请",
        AccountPortal.ADMIN,
        "merchant:review",
        "/admin/merchant-applications",
        Map.of(),
        List.of());
    add(
        "pending_campus_reviews",
        "查询待审核校园认证，不读取身份原文",
        AccountPortal.ADMIN,
        "campus:review",
        "/admin/campus-verifications",
        Map.of(),
        List.of());
    add(
        "pending_coupon_reviews",
        "查询待审核优惠请求",
        AccountPortal.ADMIN,
        "coupon:review",
        "/admin/coupon-review-requests",
        Map.of(),
        List.of());
    add(
        "pending_community_reviews",
        "查询待审核社区帖子",
        AccountPortal.ADMIN,
        "community:moderate",
        "/admin/post-review-requests",
        Map.of(),
        List.of());
    add(
        "recent_audit_events",
        "查询已授权的操作审计摘要",
        AccountPortal.ADMIN,
        "audit:read",
        "/admin/audit-events",
        Map.of(),
        List.of());
    add(
        "list_roles",
        "查询角色和所属端，不修改授权",
        AccountPortal.ADMIN,
        "rbac:manage",
        "/admin/roles",
        Map.of(),
        List.of());
  }

  private void add(
      String name,
      String description,
      AccountPortal portal,
      String permission,
      String path,
      Map<String, String> parameters,
      List<String> required) {
    definitions.add(
        new Definition(
            new Tool(name, description, parameters, required), portal, permission, path));
  }

  public void onApplicationEvent(WebServerInitializedEvent event) {
    if (event.getApplicationContext().getServerNamespace() == null)
      port = event.getWebServer().getPort();
  }

  public List<Tool> available() {
    var result =
        new ArrayList<>(definitions.stream().filter(this::allowed).map(Definition::tool).toList());
    result.addAll(business.definitions());
    return List.copyOf(result);
  }

  private boolean allowed(Definition d) {
    if (CurrentActor.portal() != d.portal() && !CurrentActor.isBusinessAdministrator())
      return false;
    if (d.permission() == null) return true;
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals(d.permission()));
  }

  public String execute(String name, String arguments) {
    if (name.equals("business_api_catalog") || name.equals("execute_business_api"))
      return business.execute(name, arguments);
    var d =
        definitions.stream()
            .filter(x -> x.tool().name().equals(name))
            .findFirst()
            .orElseThrow(
                () ->
                    new ApplicationException(HttpStatus.FORBIDDEN, "TOOL_NOT_ALLOWED", "该助手工具不可用"));
    if (!allowed(d))
      throw new ApplicationException(HttpStatus.FORBIDDEN, "TOOL_NOT_ALLOWED", "当前账号无权使用该助手工具");
    try {
      var args = json.readTree(arguments);
      if (!args.isObject()) throw new IllegalArgumentException();
      for (var names = args.fieldNames(); names.hasNext(); )
        if (!d.tool().parameters().containsKey(names.next())) throw new IllegalArgumentException();
      for (String required : d.tool().required())
        if (!args.path(required).isTextual() || args.path(required).asText().isBlank())
          throw new IllegalArgumentException();
      String path = d.path();
      if (path.contains("{merchantId}")) {
        String id = args.path("merchantId").asText();
        if (!id.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException();
        path = path.replace("{merchantId}", id);
      }
      String query = "?page=1&size=" + Math.min(p.retrievalLimit(), 20);
      if (name.startsWith("pending_")) query += "&status=PENDING";
      if (args.has("keyword")) {
        if (!args.path("keyword").isTextual() || args.path("keyword").asText().length() > 100)
          throw new IllegalArgumentException();
        query +=
            "&keyword=" + URLEncoder.encode(args.path("keyword").asText(), StandardCharsets.UTF_8);
      }
      if (port < 1) throw new IllegalStateException();
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (!(auth instanceof JwtAuthenticationToken jwt)) throw new IllegalStateException();
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path + query))
              .timeout(p.toolTimeout())
              .header("Authorization", "Bearer " + jwt.getToken().getTokenValue())
              .GET()
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var stream = response.body()) {
        bytes = stream.readNBytes(1024 * 1024 + 1);
      }
      if (response.statusCode() != 200)
        return json.writeValueAsString(
            Map.of("status", response.statusCode(), "error", "API_ACCESS_DENIED_OR_UNAVAILABLE"));
      if (bytes.length > 1024 * 1024) throw new IllegalStateException();
      String result = json.writeValueAsString(sanitize(json.readTree(bytes)));
      return result.length() > p.maxContextChars()
          ? json.writeValueAsString(Map.of("error", "RESULT_TOO_LARGE", "hint", "请缩小查询范围"))
          : result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "{\"error\":\"TOOL_UNAVAILABLE\"}";
    } catch (ApplicationException e) {
      throw e;
    } catch (Exception invalid) {
      return "{\"error\":\"INVALID_ARGUMENTS_OR_TOOL_UNAVAILABLE\"}";
    }
  }

  private static final Set<String> SAFE =
      Set.of(
          "items",
          "total",
          "page",
          "size",
          "id",
          "merchantId",
          "schoolId",
          "offerId",
          "couponId",
          "claimId",
          "orderId",
          "content",
          "name",
          "title",
          "offerTitle",
          "couponTitle",
          "status",
          "role",
          "accountType",
          "permissions",
          "code",
          "action",
          "resourceType",
          "resourceId",
          "createdAt",
          "submittedAt",
          "validFrom",
          "validUntil",
          "startsAt",
          "endsAt",
          "price",
          "amount",
          "totalAmount",
          "payableAmount",
          "paymentStatus",
          "refundStatus",
          "remainingStock",
          "totalStock",
          "couponType",
          "rules",
          "threshold",
          "amountOff",
          "discountRate",
          "description",
          "version",
          "kind",
          "memberCount",
          "requiredMembers",
          "businessHours");

  private JsonNode sanitize(JsonNode n) {
    if (n.isArray()) {
      var a = json.createArrayNode();
      n.forEach(v -> a.add(sanitize(v)));
      return a;
    }
    if (!n.isObject()) return n;
    ObjectNode object = json.createObjectNode();
    n.fields()
        .forEachRemaining(
            e -> {
              if (SAFE.contains(e.getKey())) object.set(e.getKey(), sanitize(e.getValue()));
            });
    return object;
  }
}
