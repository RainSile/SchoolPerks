package com.campus.perks.assistant.serviceImpl;

import com.campus.perks.assistant.service.AssistantProperties;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.AiModelPort.Tool;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AssistantBusinessApiTools implements ApplicationListener<WebServerInitializedEvent> {
  private static final String KEY_PREFIX = "assistant_";
  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
  private final AssistantApiCatalog catalog;
  private final ObjectMapper json;
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final AssistantProperties properties;
  private final HttpClient http;
  private volatile int port;

  public AssistantBusinessApiTools(
      AssistantApiCatalog catalog,
      ObjectMapper json,
      JdbcTemplate db,
      PlatformTransactionManager manager,
      AssistantProperties properties) {
    this.catalog = catalog;
    this.json = json;
    this.db = db;
    this.properties = properties;
    tx = new TransactionTemplate(manager);
    http =
        HttpClient.newBuilder()
            .connectTimeout(properties.toolTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public void onApplicationEvent(WebServerInitializedEvent event) {
    if (event.getApplicationContext().getServerNamespace() == null)
      port = event.getWebServer().getPort();
  }

  public List<Tool> definitions() {
    return List.of(
        new Tool(
            "business_api_catalog",
            "查找当前账号可调用的业务API；先按keyword查询摘要，再用operationId读取参数schema。写操作需用户允许；遵守版本/状态/本人或本店范围。",
            Map.of(
                "keyword",
                "功能关键词，例如优惠、订单、审核；支持路径片段",
                "operationId",
                "选中的接口ID，获取完整path/query/body结构"),
            List.of()),
        new Tool(
            "execute_business_api",
            "调用目录中的一个真实业务API。path/query/body均为JSON对象字符串；body直接填写schema.properties中的字段，不额外包装。未使用的参数传\"{}\"或省略。方法地址由服务器固定，身份不可指定。先查询实际ID和version，检查status后才能宣称成功。",
            Map.of(
                "operationId",
                "目录返回的接口ID",
                "path",
                "路径参数JSON对象，例如{\"merchantId\":\"123\"}",
                "query",
                "查询参数JSON对象，可省略",
                "body",
                "请求体JSON对象，可省略"),
            List.of("operationId")));
  }

  public String execute(String name, String arguments) {
    try {
      JsonNode input = json.readTree(arguments);
      if (!input.isObject()) throw new IllegalArgumentException();
      Set<String> fields =
          name.equals("business_api_catalog")
              ? Set.of("keyword", "operationId")
              : Set.of("operationId", "path", "query", "body");
      for (var iterator = input.fieldNames(); iterator.hasNext(); )
        if (!fields.contains(iterator.next())) throw new IllegalArgumentException();
      if (name.equals("business_api_catalog")) {
        if (input.hasNonNull("operationId"))
          return json.writeValueAsString(catalog.require(input.get("operationId").asText()));
        String keyword = input.path("keyword").asText("").toLowerCase(Locale.ROOT);
        var found =
            catalog.available().stream()
                .filter(
                    api ->
                        (api.path() + " " + api.summary())
                            .toLowerCase(Locale.ROOT)
                            .contains(keyword))
                .toList();
        return json.writeValueAsString(
            Map.of(
                "total",
                found.size(),
                "items",
                found.stream()
                    .limit(20)
                    .map(
                        api ->
                            Map.of(
                                "operationId",
                                api.id(),
                                "method",
                                api.method(),
                                "path",
                                api.path(),
                                "summary",
                                api.summary(),
                                "mutation",
                                api.mutation()))
                    .toList()));
      }
      var api = catalog.require(input.path("operationId").asText());
      JsonNode path = object(input, "path"),
          query = object(input, "query"),
          body = object(input, "body");
      validateBody(body, api.body());
      for (var keys = path.fieldNames(); keys.hasNext(); )
        if (!api.pathParameters().contains(keys.next())) throw new IllegalArgumentException();
      for (var keys = query.fieldNames(); keys.hasNext(); )
        if (!api.query().containsKey(keys.next())) throw new IllegalArgumentException();
      String resource = api.path();
      for (String parameter : api.pathParameters()) {
        String value = path.path(parameter).asText();
        if (!value.matches("[A-Za-z0-9_-]{1,100}"))
          throw new IllegalArgumentException("缺少或无效路径参数：" + parameter);
        resource = resource.replace("{" + parameter + "}", value);
      }
      if (query.size() > 0) {
        List<String> pairs = new ArrayList<>();
        query
            .fields()
            .forEachRemaining(
                e -> {
                  if (e.getValue().isContainerNode()) throw new IllegalArgumentException();
                  pairs.add(encode(e.getKey()) + "=" + encode(e.getValue().asText()));
                });
        resource += "?" + String.join("&", pairs);
      }
      var scope = AssistantActionScope.current();
      int ordinal = 0;
      String fingerprint = hash(api.id() + "\n" + resource + "\n" + canonical(body));
      if (api.mutation()) {
        if (scope == null || !scope.allowWrites())
          throw new ApplicationException(
              HttpStatus.FORBIDDEN, "ASSISTANT_WRITE_CONSENT_REQUIRED", "请先勾选允许本次业务操作");
        ordinal = scope.mutations().incrementAndGet();
        final int sequence = ordinal;
        String replay =
            tx.execute(
                status -> {
                  var lease =
                      db.queryForList(
                          "SELECT id FROM campus_assistant_turn WHERE id=? AND status='RUNNING' AND"
                              + " lease_token=? AND locked_until>UTC_TIMESTAMP(3) FOR UPDATE",
                          scope.turnId(),
                          scope.leaseToken());
                  if (lease.isEmpty())
                    throw new ApplicationException(
                        HttpStatus.CONFLICT, "ASSISTANT_LEASE_CHANGED", "处理状态已更新，不能继续执行操作");
                  var prior =
                      db.queryForList(
                          "SELECT operation_id,input_hash,status,response_body FROM"
                              + " campus_assistant_tool_execution WHERE turn_id=? AND ordinal=?",
                          scope.turnId(),
                          sequence);
                  if (!prior.isEmpty()) {
                    var row = prior.get(0);
                    if (!api.id().equals(row.get("operation_id"))
                        || !fingerprint.equals(row.get("input_hash")))
                      throw new ApplicationException(
                          HttpStatus.CONFLICT,
                          "ASSISTANT_ACTION_REPLAY_CHANGED",
                          "重试操作与原操作不一致，请先核实原结果");
                    if (!"COMPLETED".equals(row.get("status")))
                      throw new ApplicationException(
                          HttpStatus.CONFLICT,
                          "ASSISTANT_ACTION_UNCERTAIN",
                          "原操作结果尚不确定，不能重复提交，请先查询业务状态");
                    return (String) row.get("response_body");
                  }
                  if (db.queryForObject(
                          "SELECT COUNT(*) FROM campus_assistant_tool_execution WHERE turn_id=? AND"
                              + " status='RUNNING'",
                          Integer.class,
                          scope.turnId())
                      > 0)
                    throw new ApplicationException(
                        HttpStatus.CONFLICT,
                        "ASSISTANT_ACTION_UNCERTAIN",
                        "有操作结果尚不确定，请先查询，不能继续提交写操作");
                  var same =
                      db.queryForList(
                          "SELECT response_body FROM campus_assistant_tool_execution WHERE"
                              + " turn_id=? AND operation_id=? AND input_hash=? AND"
                              + " status='COMPLETED' ORDER BY ordinal LIMIT 1",
                          scope.turnId(),
                          api.id(),
                          fingerprint);
                  if (!same.isEmpty()) return (String) same.get(0).get("response_body");
                  db.update(
                      "INSERT INTO"
                          + " campus_assistant_tool_execution(turn_id,ordinal,operation_id,input_hash,status)"
                          + " VALUES(?,?,?,?,'RUNNING')",
                      scope.turnId(),
                      sequence,
                      api.id(),
                      fingerprint);
                  return null;
                });
        if (replay != null) return replay;
      }
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (!(authentication instanceof JwtAuthenticationToken jwt) || port < 1)
        throw new IllegalStateException();
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + resource))
              .timeout(properties.toolTimeout())
              .header("Authorization", "Bearer " + jwt.getToken().getTokenValue())
              .header("Accept", "application/json");
      if (api.mutation())
        request.header(
            "Idempotency-Key",
            KEY_PREFIX
                + hash(scope.turnId() + ":" + ordinal)
                    .substring(0, MAX_IDEMPOTENCY_KEY_LENGTH - KEY_PREFIX.length()));
      request.method(
          api.method(),
          api.mutation()
              ? HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
              : HttpRequest.BodyPublishers.noBody());
      if (api.mutation()) request.header("Content-Type", "application/json");
      var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var stream = response.body()) {
        bytes = stream.readNBytes(1024 * 1024 + 1);
      }
      if (bytes.length > 1024 * 1024) throw new IllegalStateException();
      JsonNode data = bytes.length == 0 ? json.nullNode() : sanitize(json.readTree(bytes));
      String result =
          json.writeValueAsString(
              Map.of("status", response.statusCode(), "operationId", api.id(), "data", data));
      if (result.length() > properties.maxContextChars()) {
        var summary = json.createObjectNode();
        for (String field :
            List.of(
                "id",
                "status",
                "version",
                "merchantId",
                "schoolId",
                "couponId",
                "offerId",
                "orderId",
                "claimId",
                "reviewStatus",
                "reviewRequestId",
                "total",
                "detail",
                "title")) {
          var value = data.get(field);
          if (value != null && value.isValueNode()) {
            summary.set(
                field,
                value.isTextual() && value.asText().length() > 256
                    ? json.getNodeFactory().textNode(value.asText().substring(0, 256))
                    : value);
            if (json.writeValueAsString(
                        Map.of(
                            "status",
                            response.statusCode(),
                            "operationId",
                            api.id(),
                            "data",
                            summary,
                            "truncated",
                            true,
                            "hint",
                            "结果已摘要；依据真实ID查询详细内容，不要重复提交写操作"))
                    .length()
                > properties.maxContextChars()) summary.remove(field);
          }
        }
        result =
            json.writeValueAsString(
                Map.of(
                    "status",
                    response.statusCode(),
                    "operationId",
                    api.id(),
                    "data",
                    summary,
                    "truncated",
                    true,
                    "hint",
                    "结果已摘要；依据真实ID查询详细内容，不要重复提交写操作"));
      }
      if (api.mutation())
        db.update(
            "UPDATE campus_assistant_tool_execution SET"
                + " status='COMPLETED',response_body=?,completed_at=UTC_TIMESTAMP(3) WHERE"
                + " turn_id=? AND ordinal=? AND status='RUNNING'",
            result,
            scope.turnId(),
            ordinal);
      return result;
    } catch (ApplicationException denied) {
      throw denied;
    } catch (IllegalArgumentException invalid) {
      try {
        return json.writeValueAsString(
            Map.of(
                "error",
                "TOOL_ARGUMENTS_INVALID",
                "detail",
                Objects.toString(
                    invalid.getMessage(), "请按目录schema填写；path/query/body使用JSON对象字符串，不额外包装")));
      } catch (Exception ignored) {
        return "{\"error\":\"TOOL_ARGUMENTS_INVALID\"}";
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return "{\"error\":\"TOOL_INTERRUPTED_OR_RESULT_UNCERTAIN\"}";
    } catch (Exception error) {
      return "{\"error\":\"INVALID_ARGUMENTS_OR_RESULT_UNCERTAIN\"}";
    }
  }

  private JsonNode object(JsonNode input, String field) throws Exception {
    if (!input.hasNonNull(field)) return json.createObjectNode();
    if (!input.get(field).isTextual())
      throw new IllegalArgumentException(field + "必须是JSON对象字符串，例如\"{}\"");
    JsonNode value = json.readTree(input.get(field).asText());
    if (!value.isObject()) throw new IllegalArgumentException();
    return value;
  }

  private void validateBody(JsonNode body, Map<String, Object> schema) {
    var properties = schema.get("properties");
    if (!(properties instanceof Map<?, ?> fields)) {
      if (schema.isEmpty() && body.size() > 0) throw new IllegalArgumentException();
      return;
    }
    body.fields()
        .forEachRemaining(
            entry -> {
              if (!fields.containsKey(entry.getKey()))
                throw new IllegalArgumentException("body字段不在schema中；允许的字段为：" + fields.keySet());
              if (entry.getValue().isObject()) {
                @SuppressWarnings("unchecked")
                var nested = (Map<String, Object>) fields.get(entry.getKey());
                validateBody(entry.getValue(), nested);
              }
            });
  }

  private String canonical(JsonNode value) throws Exception {
    if (value.isObject()) {
      var sorted = json.createObjectNode();
      var keys = new TreeSet<String>();
      value.fieldNames().forEachRemaining(keys::add);
      for (String key : keys) sorted.set(key, json.readTree(canonical(value.get(key))));
      return json.writeValueAsString(sorted);
    }
    if (value.isArray()) {
      var array = json.createArrayNode();
      for (var item : value) array.add(json.readTree(canonical(item)));
      return json.writeValueAsString(array);
    }
    return json.writeValueAsString(value);
  }

  private static final Set<String> PRIVATE =
      Set.of(
          "accesstoken",
          "refreshtoken",
          "password",
          "passwordhash",
          "codehash",
          "studentnumber",
          "realname",
          "realnameciphertext",
          "studentnumberciphertext",
          "phone",
          "identity",
          "checkouturl",
          "uploadurl",
          "downloadurl",
          "url",
          "code",
          "immutablekey",
          "stagingkey",
          "objectkey",
          "secret",
          "securitycode");

  private JsonNode sanitize(JsonNode value) {
    if (value.isArray()) {
      ArrayNode array = json.createArrayNode();
      value.forEach(v -> array.add(sanitize(v)));
      return array;
    }
    if (!value.isObject()) return value;
    ObjectNode result = json.createObjectNode();
    value
        .fields()
        .forEachRemaining(
            e -> {
              if (!PRIVATE.contains(e.getKey().toLowerCase(Locale.ROOT)))
                result.set(e.getKey(), sanitize(e.getValue()));
            });
    return result;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }
}
