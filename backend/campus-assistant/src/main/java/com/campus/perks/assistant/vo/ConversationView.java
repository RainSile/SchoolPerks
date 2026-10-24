package com.campus.perks.assistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "会话资源")
public record ConversationView(
    @Schema(description = "会话 ID") String id,
    @Schema(description = "账号所属端") String accountType,
    java.time.Instant createdAt) {}
