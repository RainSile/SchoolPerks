package com.campus.perks.assistant.controller;

import com.campus.perks.assistant.dto.*;
import com.campus.perks.assistant.service.MarketingDraftService;
import com.campus.perks.assistant.vo.MarketingViews.*;
import com.campus.perks.common.api.PageResponse;
import com.campus.perks.common.exception.ApplicationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/marketing-drafts")
@Tag(name = "AI营销草稿")
@SecurityRequirement(name = "bearerAuth")
public class MarketingDraftController {
  private final ObjectProvider<MarketingDraftService> services;

  public MarketingDraftController(ObjectProvider<MarketingDraftService> services) {
    this.services = services;
  }

  private MarketingDraftService service() {
    var s = services.getIfAvailable();
    if (s == null)
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "MARKETING_AI_UNAVAILABLE", "营销草稿服务尚未启用");
    return s;
  }

  @GetMapping("/capabilities")
  @Operation(summary = "查询本人门店的营销生成能力、文本限制及当天调用预算")
  public ResponseEntity<Capabilities> capabilities(@PathVariable String merchantId) {
    return noStore(service().capabilities(merchantId));
  }

  @GetMapping
  @Operation(summary = "分页查看本人有权经营门店的私有营销草稿")
  public ResponseEntity<PageResponse<Draft>> list(
      @PathVariable String merchantId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return noStore(service().list(merchantId, page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "查询营销草稿及生成状态，重新校验来源，不调用模型")
  public ResponseEntity<Draft> get(@PathVariable String merchantId, @PathVariable String id) {
    return noStore(service().get(merchantId, id));
  }

  @PostMapping
  @Operation(
      summary = "根据本店公开事实生成私有营销草稿",
      description = "幂等键必填；同键不同内容409，处理中重放202；仅店主/店长；无发布或交易能力。")
  public ResponseEntity<Draft> create(
      @PathVariable String merchantId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateMarketingDraftRequest request) {
    return result(merchantId, service().generate(merchantId, key, request));
  }

  @PostMapping("/{id}/generation-attempts")
  @Operation(summary = "原创建者重试失败或租约过期的生成；达到上限后409", description = "版本校验；不会重新生成已完成或已归档的草稿。")
  public ResponseEntity<Draft> retry(
      @PathVariable String merchantId,
      @PathVariable String id,
      @Valid @RequestBody RetryMarketingDraftRequest request) {
    return result(merchantId, service().retry(merchantId, id, request));
  }

  @PatchMapping("/{id}")
  @Operation(summary = "人工编辑未归档文案草稿；按版本拒绝覆盖并发修改")
  public ResponseEntity<Draft> update(
      @PathVariable String merchantId,
      @PathVariable String id,
      @Valid @RequestBody UpdateMarketingDraftRequest request) {
    return noStore(service().update(merchantId, id, request));
  }

  @PostMapping("/{id}/status-changes")
  @Operation(summary = "归档营销草稿，仅支持ARCHIVED，不支持发布")
  public ResponseEntity<Draft> archive(
      @PathVariable String merchantId,
      @PathVariable String id,
      @Valid @RequestBody ArchiveMarketingDraftRequest request) {
    return noStore(service().archive(merchantId, id, request));
  }

  private <T> ResponseEntity<T> noStore(T value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }

  private ResponseEntity<Draft> result(String merchant, Result result) {
    var value = result.value();
    int status = "GENERATING".equals(value.status()) ? 202 : result.created() ? 201 : 200;
    return ResponseEntity.status(status)
        .location(URI.create("/api/v1/merchants/" + merchant + "/marketing-drafts/" + value.id()))
        .cacheControl(CacheControl.noStore())
        .body(value);
  }
}
