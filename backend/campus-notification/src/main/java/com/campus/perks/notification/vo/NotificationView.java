package com.campus.perks.notification.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record NotificationView(
    String id, String eventId, String type, JsonNode payload, Instant createdAt, Instant readAt) {}
