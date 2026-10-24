package com.campus.perks.notification.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.*;
import com.campus.perks.notification.config.NotificationProperties;
import com.campus.perks.notification.dto.DeliveryQuery;
import com.campus.perks.notification.entity.OutboxRow;
import com.campus.perks.notification.mapper.OutboxMapper;
import com.campus.perks.notification.vo.DeliveryView;
import com.campus.perks.security.service.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class OutboxQueue implements OutboxPort {
  private final OutboxMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;
  private final NotificationProperties properties;
  private final AuditPort audit;

  public OutboxQueue(
      OutboxMapper mapper,
      ObjectMapper json,
      Clock clock,
      NotificationProperties properties,
      AuditPort audit) {
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
    this.properties = properties;
    this.audit = audit;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(EventPublisherPort.Event event) {
    try {
      mapper.insert(
          event.eventId(),
          event.aggregateId(),
          event.type(),
          json.writeValueAsString(event.payload()),
          event.occurredAt());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Outbox serialization failed");
    }
  }

  @Transactional
  public OutboxRow claim() {
    var now = clock.instant();
    mapper.exhausted(now, properties.maxAttempts());
    var row = mapper.next(now, properties.maxAttempts());
    if (row == null) return null;
    mapper.lease(row.id(), UUID.randomUUID().toString(), now.plus(properties.leaseTtl()));
    return mapper.find(row.id());
  }

  @Transactional
  public void success(OutboxRow row) {
    mapper.success(row.id(), row.leaseToken(), clock.instant());
  }

  @Transactional
  public void failure(OutboxRow row) {
    long multiplier = 1L << Math.min(Math.max(row.attempts() - 1, 0), 20);
    var delay = properties.retryBase().multipliedBy(multiplier);
    if (delay.compareTo(properties.retryMax()) > 0) delay = properties.retryMax();
    mapper.failure(
        row.id(),
        row.leaseToken(),
        row.attempts() >= properties.maxAttempts() ? "FAILED" : "PENDING",
        clock.instant().plus(delay));
  }

  private DeliveryView view(OutboxRow row) {
    return new DeliveryView(
        row.id(),
        row.aggregateId(),
        row.eventType(),
        row.status(),
        row.attempts(),
        row.nextAttemptAt(),
        row.publishedAt(),
        row.lastErrorCode());
  }

  public PageResponse<DeliveryView> list(DeliveryQuery q) {
    CurrentActor.require("notification:manage");
    return new PageResponse<>(
        mapper.list(q, (long) (q.getPage() - 1) * q.getSize()).stream().map(this::view).toList(),
        mapper.count(q.getStatus()),
        q.getPage(),
        q.getSize());
  }

  public DeliveryView get(String id) {
    CurrentActor.require("notification:manage");
    var row = mapper.find(id);
    if (row == null)
      throw new ApplicationException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", "通知投递记录不存在");
    return view(row);
  }

  @Transactional
  public DeliveryView retry(String id) {
    CurrentActor.require("notification:manage");
    var row = mapper.lock(id);
    if (row == null)
      throw new ApplicationException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", "通知投递记录不存在");
    if ("PENDING".equals(row.status())) return view(row);
    if (!"FAILED".equals(row.status()))
      throw new ApplicationException(HttpStatus.CONFLICT, "DELIVERY_NOT_FAILED", "仅失败的投递可以重新排队");
    mapper.retry(id, clock.instant());
    audit.record(CurrentActor.id(), "NOTIFICATION_RETRY_REQUESTED", "OUTBOX_EVENT", id, Map.of());
    return get(id);
  }
}
