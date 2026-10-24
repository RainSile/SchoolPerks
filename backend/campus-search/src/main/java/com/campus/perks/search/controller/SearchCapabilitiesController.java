package com.campus.perks.search.controller;

import com.campus.perks.integration.port.SearchEnginePort;
import com.campus.perks.search.service.SearchProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "搜索与地图能力")
@EnableConfigurationProperties(SearchProperties.class)
public class SearchCapabilitiesController {
  private final SearchProperties config;
  private final Environment env;
  private final SearchEnginePort engine;
  private final org.springframework.beans.factory.ObjectProvider<
          com.campus.perks.integration.port.MapServicePort>
      maps;

  public SearchCapabilitiesController(
      SearchProperties config,
      Environment env,
      SearchEnginePort engine,
      org.springframework.beans.factory.ObjectProvider<
              com.campus.perks.integration.port.MapServicePort>
          maps) {
    this.config = config;
    this.env = env;
    this.engine = engine;
    this.maps = maps;
  }

  public record Capabilities(
      boolean enabled,
      String backend,
      boolean elasticsearchConfigured,
      boolean mapSdkAvailable,
      String coordinateSystem,
      int maxWindow,
      int maxRadiusMeters) {}

  @GetMapping("/api/v1/search-capabilities")
  @Operation(summary = "搜索后端、窗口与坐标系；configured不代表远端健康检查通过")
  public Capabilities capabilities() {
    return new Capabilities(
        env.getProperty("campus.auth.enabled", Boolean.class, false),
        config.backend(),
        engine.available(),
        maps.getIfAvailable() != null,
        "WGS84",
        config.maxWindow(),
        env.getProperty("campus.location.max-radius-meters", Integer.class));
  }
}
