package com.campus.perks.integration.port;

import java.util.List;
import java.util.Map;

/** Pinecone 只保存检索副本，MySQL 始终是优惠库存及状态的事实来源。 */
public interface VectorSearchPort {
  List<Match> search(String scopeId, float[] embedding, int limit);

  void upsert(String scopeId, String documentId, float[] embedding, Map<String, String> metadata);

  void delete(String scopeId, String documentId);

  record Match(String documentId, double score, Map<String, String> metadata) {}
}
