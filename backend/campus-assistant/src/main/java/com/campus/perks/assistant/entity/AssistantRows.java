package com.campus.perks.assistant.entity;

import java.time.Instant;

public final class AssistantRows {
  private AssistantRows() {}

  public record Conversation(String id, String owner, String portal, Instant createdAt) {}

  public record Turn(
      String id,
      String conversationId,
      String requestKey,
      String inputHash,
      String input,
      String status,
      String token,
      Instant lockedUntil,
      String errorCode,
      Instant createdAt,
      int attempt) {}
}
