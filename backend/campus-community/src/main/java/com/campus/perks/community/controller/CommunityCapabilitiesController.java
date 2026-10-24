package com.campus.perks.community.controller;

import com.campus.perks.community.service.CommunityProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "社区能力")
@EnableConfigurationProperties(CommunityProperties.class)
public class CommunityCapabilitiesController {
  private final Environment env;
  private final CommunityProperties config;

  public CommunityCapabilitiesController(Environment env, CommunityProperties config) {
    this.env = env;
    this.config = config;
  }

  public record Capabilities(
      boolean enabled,
      String persistence,
      boolean mongoAvailable,
      boolean automaticModerationAvailable,
      int maxImages,
      int maxBodyLength,
      int maxCommentLength) {}

  @GetMapping("/api/v1/community-capabilities")
  @Operation(summary = "社区可用性、人工审核策略及内容上限，不含私有配置")
  public Capabilities capabilities() {
    return new Capabilities(
        env.getProperty("campus.auth.enabled", Boolean.class, false),
        "MYSQL",
        false,
        false,
        config.maxImages(),
        config.maxBodyLength(),
        config.maxCommentLength());
  }
}
