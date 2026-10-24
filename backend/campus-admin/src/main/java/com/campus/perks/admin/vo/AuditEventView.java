package com.campus.perks.admin.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "业务关键操作审计，不含凭据、身份原文和私有对象 key")
public record AuditEventView(
    String id,
    String actorId,
    String action,
    String resourceType,
    String resourceId,
    JsonNode details,
    Instant createdAt) {}
