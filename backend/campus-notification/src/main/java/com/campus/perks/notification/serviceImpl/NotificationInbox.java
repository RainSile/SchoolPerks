package com.campus.perks.notification.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.EventPublisherPort;
import com.campus.perks.notification.entity.NotificationRow;
import com.campus.perks.notification.mapper.NotificationMapper;
import com.campus.perks.notification.service.NotificationQueries;
import com.campus.perks.notification.vo.NotificationView;
import com.campus.perks.security.service.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class NotificationInbox implements NotificationQueries {
  private final NotificationMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public NotificationInbox(NotificationMapper mapper, ObjectMapper json, Clock clock) {
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  public NotificationView view(NotificationRow row) {
    try {
      return new NotificationView(
          row.id(),
          row.eventId(),
          row.eventType(),
          json.readTree(row.payload()),
          row.createdAt(),
          row.readAt());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Notification payload invalid");
    }
  }

  @Transactional
  public NotificationView deliver(EventPublisherPort.Event event) {
    List<String> resourceKeys =
        switch (event.type()) {
          case "COUPON_REDEEMED" -> List.of("claimId", "redemptionId", "merchantId");
          case "ORDER_CREATED",
                  "ORDER_PAID",
                  "ORDER_CANCELLED",
                  "ORDER_GROUP_SUCCEEDED",
                  "ORDER_REFUND_PENDING",
                  "ORDER_REFUNDED",
                  "ORDER_REDEEMED" ->
              List.of("orderId", "merchantId");
          default -> throw new IllegalArgumentException("Unsupported event type");
        };
    String user = Objects.toString(event.payload().get("userId"), "");
    if (!user.matches("[1-9][0-9]{0,17}"))
      throw new IllegalArgumentException("Invalid event recipient");
    var payload = new LinkedHashMap<String, String>();
    for (String key : resourceKeys) {
      String value = Objects.toString(event.payload().get(key), "");
      if (!value.matches("[1-9][0-9]{0,17}"))
        throw new IllegalArgumentException("Invalid event resource");
      payload.put(key, value);
    }
    try {
      mapper.insert(
          event.eventId(),
          event.eventId(),
          Long.parseLong(user),
          event.type(),
          json.writeValueAsString(payload),
          event.occurredAt());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Notification serialization failed");
    }
    return view(mapper.event(event.eventId(), Long.parseLong(user)));
  }

  public PageResponse<NotificationView> list(PageQuery q) {
    long actor = Long.parseLong(CurrentActor.id());
    return new PageResponse<>(
        mapper.list(actor, q.getSize(), (long) (q.getPage() - 1) * q.getSize()).stream()
            .map(this::view)
            .toList(),
        mapper.count(actor),
        q.getPage(),
        q.getSize());
  }

  public NotificationView get(String id) {
    var row = mapper.own(id, Long.parseLong(CurrentActor.id()));
    if (row == null)
      throw new ApplicationException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "本人通知不存在");
    return view(row);
  }

  @Transactional
  public NotificationView read(String id) {
    get(id);
    mapper.read(id, Long.parseLong(CurrentActor.id()), clock.instant());
    return get(id);
  }
}
