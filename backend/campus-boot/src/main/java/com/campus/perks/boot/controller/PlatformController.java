package com.campus.perks.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "平台")
public class PlatformController {
  @GetMapping
  @Operation(summary = "查询工程阶段", description = "用于前端连接检查，不代表业务已完成。")
  public PlatformView get() {
    return new PlatformView("SCAFFOLD");
  }

  @Schema(description = "工程信息")
  public record PlatformView(@Schema(description = "当前阶段") String stage) {}
}
