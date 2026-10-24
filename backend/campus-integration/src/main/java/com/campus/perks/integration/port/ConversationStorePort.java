package com.campus.perks.integration.port;

import java.time.Instant;
import java.util.List;

public interface ConversationStorePort {
  record Source(String type, String id, String title, String excerpt, String hash) {}

  record Turn(
      String id,
      String conversationId,
      String owner,
      String portal,
      String question,
      String answer,
      List<String> tools,
      List<Source> sources,
      Instant createdAt,
      int attempt) {}

  Turn completed(String id, String owner, String portal);

  List<Turn> history(String conversation, String owner, String portal, int limit);

  void save(Turn turn);
}
