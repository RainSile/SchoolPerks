package com.campus.perks.assistant.controller;

import com.campus.perks.assistant.dto.*;
import com.campus.perks.assistant.service.*;
import com.campus.perks.assistant.vo.*;
import com.campus.perks.assistant.vo.AssistantViews.*;
import com.campus.perks.common.exception.ApplicationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "悬浮智能助手")
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantController {
  private final ObjectProvider<AssistantService> services;
  private final AssistantProperties p;

  public AssistantController(ObjectProvider<AssistantService> services, AssistantProperties p) {
    this.services = services;
    this.p = p;
  }

  private AssistantService service() {
    var s = services.getIfAvailable();
    if (s == null)
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_UNAVAILABLE", "助手服务尚未启用");
    return s;
  }

  @GetMapping("/capabilities")
  @Operation(summary = "查询助手配置可用性、当前端工具和消息限制，不返回凭据")
  public Capabilities capabilities() {
    var s = services.getIfAvailable();
    return s == null
        ? new Capabilities(
            false, null, p.maxMessageLength(), (int) p.leaseTtl().toSeconds(), List.of())
        : s.capabilities();
  }

  @PostMapping("/conversations")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "创建当前账号和所属端独立的助手会话，空JSON请求体")
  public ResponseEntity<ConversationView> create(@Valid @RequestBody CreateConversationRequest r) {
    var v = service().create(r);
    return ResponseEntity.created(URI.create("/api/v1/assistant/conversations/" + v.id()))
        .header("Cache-Control", "no-store")
        .body(v);
  }

  @GetMapping("/conversations/{id}/messages")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "读取本人同端会话的最近已完成消息；重新校验公开来源")
  public ResponseEntity<List<Message>> history(@PathVariable String id) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(service().history(id));
  }

  @PostMapping("/conversations/{id}/messages")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "本人提问；幂等键必填，同键不同内容409，处理中的重放202")
  public ResponseEntity<Message> send(
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody SendMessageRequest r) {
    var result = service().send(id, key, r);
    var v = result.value();
    int status = "COMPLETED".equals(v.status()) ? (result.created() ? 201 : 200) : 202;
    return ResponseEntity.status(status)
        .location(URI.create("/api/v1/assistant/conversations/" + id + "/messages/" + v.id()))
        .header("Cache-Control", "no-store")
        .body(v);
  }

  @GetMapping("/conversations/{id}/messages/{messageId}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "查询本人消息处理状态；不会重复调用模型")
  public ResponseEntity<Message> message(@PathVariable String id, @PathVariable String messageId) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(service().message(id, messageId));
  }
}
