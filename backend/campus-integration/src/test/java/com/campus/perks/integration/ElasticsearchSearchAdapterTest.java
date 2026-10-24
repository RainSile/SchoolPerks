package com.campus.perks.integration;

import static org.assertj.core.api.Assertions.*;

import com.campus.perks.integration.adapter.ElasticsearchSearchAdapter;
import com.campus.perks.integration.config.ElasticsearchProperties;
import com.campus.perks.integration.port.SearchEnginePort.Document;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;

/** Real Java HTTP client against a bounded local protocol fixture, not a real ES/IK cluster. */
class ElasticsearchSearchAdapterTest {
  private HttpServer server;
  private final ObjectMapper json = new ObjectMapper();
  private ElasticsearchSearchAdapter adapter;
  private String version = "8.17.3", ik = "8.17.3", analyzer = "ik_max_word";
  private boolean aliasMissing;
  private long revision;
  private boolean visible;
  private JsonNode query, indexed;
  private final List<String> writes = new ArrayList<>();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          int code = 200;
          Object value;
          if (!"ApiKey test-only".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            code = 401;
            value = Map.of("error", "missing test auth");
          } else if (path.equals("/")) value = Map.of("version", Map.of("number", version));
          else if (path.equals("/_nodes/plugins"))
            value =
                Map.of(
                    "nodes",
                    Map.of(
                        "node1",
                        Map.of("plugins", List.of(Map.of("name", "analysis-ik", "version", ik)))));
          else if (path.equals("/_alias/campus-test-search")) {
            if (aliasMissing) {
              code = 404;
              value = Map.of("error", "alias missing");
            } else
              value =
                  Map.of(
                      "campus-test-physical",
                      Map.of(
                          "aliases", Map.of("campus-test-search", Map.of("is_write_index", true))));
          } else if (path.endsWith("/_mapping")) {
            var fields = new HashMap<String, Object>();
            for (String key :
                List.of("resource_type", "resource_id", "source_hash", "school_id", "merchant_id"))
              fields.put(key, Map.of("type", "keyword"));
            for (String key : List.of("title", "body"))
              fields.put(
                  key, Map.of("type", "text", "analyzer", analyzer, "search_analyzer", "ik_smart"));
            fields.put("visible", Map.of("type", "boolean"));
            value =
                Map.of(
                    "campus-test-physical",
                    Map.of(
                        "mappings",
                        Map.of(
                            "dynamic",
                            "strict",
                            "_meta",
                            Map.of("campus_schema", 1),
                            "properties",
                            fields)));
          } else if (path.contains("/_doc/")) {
            writes.add(exchange.getRequestURI().toString());
            indexed = json.readTree(exchange.getRequestBody());
            var params = new HashMap<String, String>();
            for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
              String[] p = pair.split("=");
              params.put(p[0], p[1]);
            }
            assertThat(params.get("require_alias")).isEqualTo("true");
            assertThat(params.get("version_type")).isEqualTo("external_gte");
            long incoming = Long.parseLong(params.get("version"));
            if (incoming < revision) {
              code = 409;
              value = Map.of("error", Map.of("type", "version_conflict_engine_exception"));
            } else {
              revision = incoming;
              visible = indexed.path("visible").asBoolean();
              value = Map.of("result", "updated");
            }
          } else if (path.endsWith("/_search")) {
            query = json.readTree(exchange.getRequestBody());
            value =
                Map.of(
                    "hits",
                    Map.of(
                        "hits",
                        List.of(
                            Map.of(
                                "_source",
                                Map.of(
                                    "resource_type",
                                    "POST",
                                    "resource_id",
                                    "5",
                                    "source_hash",
                                    "a".repeat(64))))));
          } else {
            code = 400;
            value = Map.of("error", "unexpected fixture request");
          }
          byte[] body = json.writeValueAsBytes(value);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(code, body.length);
          try (var out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
    adapter =
        new ElasticsearchSearchAdapter(
            new ElasticsearchProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-only",
                "campus-test-search",
                "8.17.3",
                "8.17.3",
                Duration.ofSeconds(2)),
            json);
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void explicitVersionIkAndMappingMustMatchBeforeWriting() {
    adapter.verifyInstallation();
    version = "8.18.0";
    assertThatThrownBy(adapter::verifyInstallation).hasMessageContaining("不可用");
    version = "8.17.3";
    ik = "8.16.0";
    assertThatThrownBy(adapter::verifyInstallation).hasMessageContaining("不可用");
    ik = "8.17.3";
    analyzer = "standard";
    assertThatThrownBy(adapter::verifyInstallation).hasMessageContaining("不可用");
    assertThat(writes).isEmpty();
  }

  @Test
  void tombstonesUseExternalVersionAndMissingAliasNeverAutoCreatesIndex() {
    adapter.index(new Document("POST", "5", "1", null, "public", "body", "a".repeat(64), true, 1));
    adapter.index(new Document("POST", "5", null, null, "", "", null, false, 2));
    adapter.index(new Document("POST", "5", "1", null, "stale", "body", "a".repeat(64), true, 1));
    assertThat(visible).isFalse();
    assertThat(revision).isEqualTo(2);
    aliasMissing = true;
    assertThatThrownBy(
            () ->
                adapter.index(
                    new Document("POST", "6", "1", null, "new", "body", "a".repeat(64), true, 1)))
        .hasMessageContaining("不可用");
    assertThat(writes).hasSize(3);
  }

  @Test
  void queryUsesOnlyWhitelistedFieldsAndLiteralJsonWithoutHtml() {
    var keys = adapter.candidates("<script> OR *", "POST", "1", 20);
    assertThat(keys).hasSize(1);
    assertThat(query.at("/_source").toString()).contains("resource_type", "source_hash");
    assertThat(query.at("/query/bool/must/0/multi_match/query").asText())
        .isEqualTo("<script> OR *");
    assertThat(query.at("/query/bool/must/0/multi_match/fields").toString())
        .isEqualTo("[\"title^3\",\"body\"]");
    assertThat(query.has("query_string")).isFalse();
  }
}
