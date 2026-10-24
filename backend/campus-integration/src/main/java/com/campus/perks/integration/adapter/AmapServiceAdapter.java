package com.campus.perks.integration.adapter;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.AmapProperties;
import com.campus.perks.integration.port.MapServicePort;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.integrations.amap.enabled", havingValue = "true")
@EnableConfigurationProperties(AmapProperties.class)
public class AmapServiceAdapter implements MapServicePort {
  private static final Set<String> PATHS =
      Set.of(
          "v3/log/init",
          "v3/assistant/coordinate/convert",
          "v3/geocode/regeo",
          "v3/geocode/geo",
          "v3/place/around",
          "v3/place/text",
          "v3/config/district",
          "v3/direction/walking",
          "v4/map/styles");
  private final AmapProperties p;
  private final HttpClient http;

  public AmapServiceAdapter(AmapProperties p) {
    this.p = p;
    if (p.jsKey().isBlank() || p.securityCode().isBlank() || p.requestsPerMinute() < 1)
      throw new IllegalStateException("Invalid AMap configuration");
    http =
        HttpClient.newBuilder()
            .connectTimeout(p.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public String jsKey() {
    return p.jsKey();
  }

  public Reply proxy(String path, Map<String, String> input) {
    if (!PATHS.contains(path) || input.size() > 40)
      throw new ApplicationException(HttpStatus.NOT_FOUND, "MAP_RESOURCE_NOT_FOUND", "地图资源不存在");
    var query = new TreeMap<String, String>();
    for (var e : input.entrySet()) {
      if (!e.getKey().matches("[A-Za-z0-9_]{1,64}") || e.getValue().length() > 2048)
        throw invalid();
      if ("callback".equals(e.getKey()) && !e.getValue().matches("[A-Za-z_$][A-Za-z0-9_.$]{0,100}"))
        throw invalid();
      if (!Set.of("key", "jscode").contains(e.getKey())) query.put(e.getKey(), e.getValue());
    }
    query.put("key", p.jsKey());
    query.put("jscode", p.securityCode());
    String args =
        query.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining("&"));
    String host = "v4/map/styles".equals(path) ? "webapi.amap.com" : "restapi.amap.com";
    try {
      var request =
          HttpRequest.newBuilder(URI.create("https://" + host + "/" + path + "?" + args))
              .timeout(p.timeout())
              .GET()
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var in = response.body()) {
        bytes = in.readNBytes(2 * 1024 * 1024 + 1);
      }
      if (response.statusCode() != 200 || bytes.length > 2 * 1024 * 1024)
        throw new IllegalStateException();
      String body =
          new String(bytes, StandardCharsets.UTF_8).replace(p.securityCode(), "[REDACTED]");
      String type =
          input.containsKey("callback")
              ? "application/javascript;charset=UTF-8"
              : "application/json;charset=UTF-8";
      return new Reply(type, body.getBytes(StandardCharsets.UTF_8));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (Exception e) {
      throw unavailable();
    }
  }

  private ApplicationException invalid() {
    return new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_MAP_REQUEST", "地图请求参数不符合要求");
  }

  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "MAP_PROVIDER_UNAVAILABLE", "地图服务暂不可用");
  }
}
