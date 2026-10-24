package com.campus.perks.merchant.controller;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.AmapProperties;
import com.campus.perks.integration.port.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "高德地图")
@EnableConfigurationProperties(AmapProperties.class)
public class MapController {
  private final ObjectProvider<MapServicePort> maps;
  private final ObjectProvider<RateLimitPort> limits;
  private final AmapProperties p;

  public MapController(
      ObjectProvider<MapServicePort> maps, ObjectProvider<RateLimitPort> limits, AmapProperties p) {
    this.maps = maps;
    this.limits = limits;
    this.p = p;
  }

  public record Capabilities(
      boolean configured,
      String provider,
      String coordinateSystem,
      String jsKey,
      String proxyPath) {}

  @GetMapping("/api/v1/map-capabilities")
  @Operation(summary = "地图配置及浏览器公开JS Key；不返回安全密钥，configured不是健康探测")
  public Capabilities capabilities() {
    var m = maps.getIfAvailable();
    return new Capabilities(
        m != null, "AMAP", "GCJ02", m == null ? null : m.jsKey(), "/api/v1/maps/_AMapService");
  }

  @GetMapping("/api/v1/maps/_AMapService/{version}/{*path}")
  @Operation(summary = "高德官方JS API安全密钥代理，固定上游和路径白名单，IP限频")
  public ResponseEntity<byte[]> proxy(
      @PathVariable String version,
      @PathVariable String path,
      @RequestParam Map<String, String> query,
      HttpServletRequest request) {
    var map = maps.getIfAvailable();
    var limiter = limits.getIfAvailable();
    if (map == null || limiter == null)
      throw new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE, "MAP_UNAVAILABLE", "地图服务尚未启用");
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(request.getRemoteAddr().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException();
    }
    if (!limiter.acquire("map:" + hash, p.requestsPerMinute(), Duration.ofMinutes(1)))
      throw new ApplicationException(HttpStatus.TOO_MANY_REQUESTS, "MAP_RATE_LIMITED", "地图请求过于频繁");
    var result = map.proxy(version + path, query);
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(result.contentType()))
        .body(result.body());
  }
}
