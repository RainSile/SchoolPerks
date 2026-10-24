package com.campus.perks.notification.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.notification.dto.DeliveryQuery;
import com.campus.perks.notification.serviceImpl.OutboxQueue;
import com.campus.perks.notification.vo.DeliveryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notification-deliveries")
@Tag(name = "通知投递恢复")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('notification:manage')")
public class NotificationDeliveryController {
  private final ObjectProvider<OutboxQueue> queues;

  public NotificationDeliveryController(ObjectProvider<OutboxQueue> queues) {
    this.queues = queues;
  }

  private OutboxQueue queue() {
    var value = queues.getIfAvailable();
    if (value == null)
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "NOTIFICATION_UNAVAILABLE", "通知投递尚未启用");
    return value;
  }

  @GetMapping
  @Operation(summary = "按状态分页查询通知投递", description = "需要notification:manage，不返回通知原始payload。")
  public PageResponse<DeliveryView> list(@Valid @ParameterObject DeliveryQuery q) {
    return queue().list(q);
  }

  @GetMapping("/{id}")
  @Operation(summary = "查询单条投递状态")
  public DeliveryView get(@PathVariable String id) {
    return queue().get(id);
  }

  @PutMapping("/{id}/retry-request")
  @Operation(summary = "将失败通知重新排队", description = "仅FAILED可重置；已PENDING时幂等；操作写入审计。")
  public DeliveryView retry(@PathVariable String id) {
    return queue().retry(id);
  }
}
