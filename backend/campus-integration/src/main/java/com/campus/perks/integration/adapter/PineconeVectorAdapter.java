package com.campus.perks.integration.adapter;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.IntegrationProperties;
import com.campus.perks.integration.port.VectorSearchPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Documents API for a named BYOV dense_vector field; never creates or mutates index schemas. */
@Component
@ConditionalOnProperty(name = "campus.integrations.pinecone.enabled", havingValue = "true")
public class PineconeVectorAdapter implements VectorSearchPort {
  private static final String API_VERSION = "2026-07";
  private static final List<String> METADATA =
      List.of(
          "visibility",
          "resource_type",
          "resource_id",
          "source_hash",
          "school_id",
          "merchant_id",
          "source_version",
          "valid_from",
          "valid_until");
  private final IntegrationProperties.Pinecone p;
  private final ObjectMapper json;
  private final HttpClient http;
  private final URI host;
  private final int dimensions;

  public PineconeVectorAdapter(IntegrationProperties config, ObjectMapper json) {
    p = config.pinecone();
    this.json = json;
    dimensions = config.ai().embeddingDimensions();
    host = URI.create(p.indexHost());
    if (!"https".equals(host.getScheme())
        || host.getHost() == null
        || !host.getHost().endsWith(".pinecone.io")
        || host.getPort() != -1
        || host.getUserInfo() != null
        || host.getQuery() != null
        || host.getFragment() != null
        || !(host.getPath().isEmpty() || host.getPath().equals("/"))
        || p.apiKey().isBlank()
        || !p.namespace().matches("[A-Za-z0-9_-]{1,64}")
        || p.vectorField() == null
        || !p.vectorField().matches("[A-Za-z][A-Za-z0-9_]{0,63}")
        || METADATA.contains(p.vectorField())
        || dimensions < 1) {
      throw new IllegalStateException("Invalid Pinecone configuration");
    }
    http =
        HttpClient.newBuilder()
            .connectTimeout(p.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  private void scope(String scope) {
    if (!p.namespace().equals(scope))
      throw new IllegalArgumentException("Invalid vector namespace");
  }

  private void vector(float[] values) {
    if (values == null || values.length != dimensions)
      throw new IllegalArgumentException("Invalid vector dimensions");
    for (float value : values)
      if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid vector value");
  }

  private JsonNode post(String action, Object body) {
    try {
      var req =
          HttpRequest.newBuilder(
                  host.resolve("/namespaces/" + p.namespace() + "/documents/" + action))
              .timeout(p.timeout())
              .header("Api-Key", p.apiKey())
              .header("X-Pinecone-API-Version", API_VERSION)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var in = response.body()) {
        bytes = in.readNBytes(2 * 1024 * 1024 + 1);
      }
      if (bytes.length > 2 * 1024 * 1024
          || response.statusCode() < 200
          || response.statusCode() >= 300) throw new IllegalStateException();
      return bytes.length == 0 ? json.createObjectNode() : json.readTree(bytes);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (Exception e) {
      throw unavailable();
    }
  }

  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "VECTOR_STORE_UNAVAILABLE", "向量检索服务暂不可用");
  }

  public List<Match> search(String scope, float[] vector, int limit) {
    scope(scope);
    vector(vector);
    if (limit < 1 || limit > 20) throw new IllegalArgumentException("Invalid vector limit");
    var result =
        post(
            "search",
            Map.of(
                "score_by",
                List.of(Map.of("type", "dense_vector", "field", p.vectorField(), "values", vector)),
                "top_k",
                limit,
                "include_fields",
                METADATA,
                "filter",
                Map.of("visibility", Map.of("$eq", "PUBLIC"))));
    var matches = new ArrayList<Match>();
    for (var match : result.path("matches")) {
      if (!match.path("_score").isNumber()) continue;
      var metadata = new HashMap<String, String>();
      for (String name : METADATA)
        if (match.hasNonNull(name)) metadata.put(name, match.path(name).asText());
      matches.add(
          new Match(
              match.path("_id").asText(), match.path("_score").asDouble(), Map.copyOf(metadata)));
    }
    return List.copyOf(matches);
  }

  public void upsert(String scope, String id, float[] vector, Map<String, String> metadata) {
    scope(scope);
    vector(vector);
    if (!"PUBLIC".equals(metadata.get("visibility")) || !METADATA.containsAll(metadata.keySet()))
      throw new IllegalArgumentException("Only public source metadata is allowed");
    var document = new HashMap<String, Object>(metadata);
    document.put("_id", id);
    document.put(p.vectorField(), vector);
    post("upsert", Map.of("documents", List.of(document)));
  }

  public void delete(String scope, String id) {
    scope(scope);
    post("delete", Map.of("ids", List.of(id)));
  }
}
