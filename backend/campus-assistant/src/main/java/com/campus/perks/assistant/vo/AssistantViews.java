package com.campus.perks.assistant.vo;

import com.campus.perks.integration.port.ConversationStorePort;
import java.time.Instant;
import java.util.List;

public final class AssistantViews {
  private AssistantViews() {}

  public record Message(
      String id,
      String conversationId,
      String status,
      String question,
      String reply,
      List<String> tools,
      List<ConversationStorePort.Source> sources,
      String errorCode,
      Instant createdAt) {}

  public record Capabilities(
      boolean enabled,
      String accountType,
      int maxMessageLength,
      int timeoutSeconds,
      List<String> tools) {}
}
