package com.campus.perks.integration.adapter;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.ElasticsearchProperties;
import com.campus.perks.integration.port.SearchEnginePort;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Elasticsearch 8 REST adapter, explicit version/IK/mapping checks, no index auto-creation. */
@Component
@ConditionalOnProperty(name = "campus.search.backend", havingValue = "ELASTICSEARCH")
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchSearchAdapter implements SearchEnginePort {
  private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
  private final ElasticsearchProperties p;
  private final ObjectMapper json;
  private final HttpClient http;
  private final URI endpoint;

  public ElasticsearchSearchAdapter(ElasticsearchProperties p, ObjectMapper json) {
    this.p = p;
    this.json = json;
    endpoint = URI.create(p.endpoint());
    if (endpoint.getHost() == null
        || endpoint.getUserInfo() != null
        || endpoint.getQuery() != null
        || endpoint.getFragment() != null
        || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
        || !("https".equals(endpoint.getScheme())
            || "http".equals(endpoint.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost()))
        || p.index() == null
        || !p.index().matches("campus-[a-z0-9-]{1,100}")
        || p.expectedVersion() == null
        || !p.expectedVersion().matches("8\\.[0-9]+\\.[0-9]+")
        || !p.expectedVersion().equals(p.ikVersion())
        || p.timeout() == null
        || p.timeout().isZero()
        || p.timeout().isNegative())
      throw new IllegalStateException(
          "Elasticsearch requires an explicit ES8 version, matching IK version, campus-* alias and"
              + " valid endpoint");
    http =
        HttpClient.newBuilder()
            .connectTimeout(p.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public boolean available() {
    return true;
  }

  private ApplicationException failure() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SEARCH_ENGINE_UNAVAILABLE",
        "Elasticsearch版本、IK、索引映射或连接不可用");
  }

  private JsonNode request(String method, String path, Object body, boolean oldVersionAllowed) {
    try {
      var builder =
          HttpRequest.newBuilder(endpoint.resolve(path))
              .timeout(p.timeout())
              .header("Accept", "application/json");
      if (p.apiKey() != null && !p.apiKey().isBlank())
        builder.header("Authorization", "ApiKey " + p.apiKey());
      builder.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(
                  json.writeValueAsString(body), StandardCharsets.UTF_8));
      if (body != null) builder.header("Content-Type", "application/json");
      var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var stream = response.body()) {
        bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
      }
      if (bytes.length > MAX_RESPONSE_BYTES) throw failure();
      var value = json.readTree(bytes);
      if (oldVersionAllowed
          && response.statusCode() == 409
          && "version_conflict_engine_exception".equals(value.path("error").path("type").asText()))
        return value;
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw failure();
      return value;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw failure();
    } catch (ApplicationException e) {
      throw e;
    } catch (Exception e) {
      throw failure();
    }
  }

  public void verifyInstallation() {
    var root = request("GET", "/", null, false);
    if (!p.expectedVersion().equals(root.path("version").path("number").asText())) throw failure();
    var nodes = request("GET", "/_nodes/plugins", null, false).path("nodes");
    if (!nodes.isObject() || nodes.isEmpty()) throw failure();
    for (var node : nodes) {
      boolean ik = false;
      for (var plugin : node.path("plugins")) {
        if ("analysis-ik".equals(plugin.path("name").asText())
            && p.ikVersion().equals(plugin.path("version").asText())) ik = true;
      }
      if (!ik) throw failure();
    }
    // Only a manually provisioned alias is accepted. require_alias also protects against deletion
    // races.
    var alias = request("GET", "/_alias/" + p.index(), null, false);
    if (alias.size() != 1) throw failure();
    var mapping = request("GET", "/" + p.index() + "/_mapping", null, false);
    if (mapping.size() != 1) throw failure();
    var m = mapping.elements().next().path("mappings");
    var fields = m.path("properties");
    if (m.path("_meta").path("campus_schema").asInt() != 1
        || !"strict".equals(m.path("dynamic").asText())
        || !"ik_max_word".equals(fields.path("title").path("analyzer").asText())
        || !"ik_smart".equals(fields.path("title").path("search_analyzer").asText())
        || !"ik_max_word".equals(fields.path("body").path("analyzer").asText())
        || !"ik_smart".equals(fields.path("body").path("search_analyzer").asText()))
      throw failure();
    for (String key :
        List.of("resource_type", "resource_id", "source_hash", "school_id", "merchant_id"))
      if (!"keyword".equals(fields.path(key).path("type").asText())) throw failure();
    if (!"boolean".equals(fields.path("visible").path("type").asText())) throw failure();
  }

  public void index(Document d) {
    verifyInstallation();
    var body = new LinkedHashMap<String, Object>();
    body.put("resource_type", d.type());
    body.put("resource_id", d.id());
    body.put("school_id", d.schoolId());
    body.put("merchant_id", d.merchantId());
    body.put("title", d.title());
    body.put("body", d.body());
    body.put("source_hash", d.sourceHash());
    body.put("visible", d.visible());
    request(
        "PUT",
        "/"
            + p.index()
            + "/_doc/"
            + d.type()
            + "-"
            + d.id()
            + "?require_alias=true&version_type=external_gte&version="
            + d.revision(),
        body,
        true);
  }

  public List<Key> candidates(String keyword, String type, String school, int window) {
    verifyInstallation();
    var filters = new ArrayList<Object>();
    filters.add(Map.of("term", Map.of("visible", true)));
    if (type != null) filters.add(Map.of("term", Map.of("resource_type", type)));
    if (school != null) filters.add(Map.of("term", Map.of("school_id", school)));
    var match =
        Map.of(
            "multi_match",
            Map.of("query", keyword, "fields", List.of("title^3", "body"), "type", "best_fields"));
    var bool = Map.of("bool", Map.of("filter", filters, "must", List.of(match)));
    var query =
        Map.of(
            "size",
            window,
            "_source",
            List.of("resource_type", "resource_id", "source_hash"),
            "query",
            bool,
            "sort",
            List.of(
                Map.of("_score", "desc"),
                Map.of("resource_type", "asc"),
                Map.of("resource_id", "asc")));
    var hits =
        request("POST", "/" + p.index() + "/_search", query, false).path("hits").path("hits");
    if (!hits.isArray() || hits.size() > window) throw failure();
    var result = new ArrayList<Key>();
    for (var hit : hits) {
      var s = hit.path("_source");
      String t = s.path("resource_type").asText(),
          id = s.path("resource_id").asText(),
          hash = s.path("source_hash").asText();
      if (!Set.of("MERCHANT", "COUPON", "OFFER", "POST").contains(t)
          || !id.matches("[1-9][0-9]{0,17}")
          || !hash.matches("[a-f0-9]{64}")) throw failure();
      result.add(new Key(t, id, hash));
    }
    return result;
  }
}
