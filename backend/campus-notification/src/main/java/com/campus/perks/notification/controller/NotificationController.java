package com.campus.perks.notification.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.notification.config.NotificationProperties;
import com.campus.perks.notification.service.NotificationQueries;
import com.campus.perks.notification.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "本人通知")
public class NotificationController {
  private final NotificationQueries service;
  private final Environment environment;
  private final NotificationProperties properties;

  public NotificationController(
      NotificationQueries service, Environment environment, NotificationProperties properties) {
    this.service = service;
    this.environment = environment;
    this.properties = properties;
  }

  @GetMapping("/notification-capabilities")
  @Operation(
      summary = "查询通知能力与连接参数",
      description = "WebSocket首帧AUTHENTICATE携带内存中的accessToken，不在URL携带凭据；服务端只发送本人事件并持续检查会话。")
  public NotificationCapabilities capabilities() {
    return new NotificationCapabilities(
        environment.getProperty("campus.auth.enabled", Boolean.class, false),
        "/api/v1/notification-stream",
        properties.pingIntervalSeconds(),
        properties.reconnectDelaySeconds());
  }

  @GetMapping("/users/me/notifications")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "分页查询本人持久通知", description = "离线或推送失败后可通过HTTP恢复；GET不修改已读状态。")
  public PageResponse<NotificationView> list(@Valid @ParameterObject PageQuery q) {
    return service.list(q);
  }

  @GetMapping("/users/me/notifications/{id}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "查询本人通知")
  public NotificationView get(@PathVariable String id) {
    return service.get(id);
  }

  @PutMapping("/users/me/notifications/{id}/read-receipt")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "幂等标记本人通知已读")
  public NotificationView read(@PathVariable String id) {
    return service.read(id);
  }
}
