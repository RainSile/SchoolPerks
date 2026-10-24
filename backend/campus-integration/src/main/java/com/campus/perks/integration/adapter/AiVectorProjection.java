package com.campus.perks.integration.adapter;

import com.campus.perks.integration.config.IntegrationProperties;
import com.campus.perks.integration.port.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.integrations.pinecone.enabled", havingValue = "true")
public class AiVectorProjection implements VectorProjectionPort {
  private final PublicContentPort content;
  private final AiModelPort model;
  private final VectorSearchPort store;
  private final String namespace;

  public AiVectorProjection(
      PublicContentPort content,
      AiModelPort model,
      VectorSearchPort store,
      IntegrationProperties p) {
    this.content = content;
    this.model = model;
    this.store = store;
    namespace = p.pinecone().namespace();
  }

  public void project(SearchEnginePort.Document document) {
    String id = document.type() + ":" + document.id();
    var current = content.find(List.of(new PublicContentPort.Key(document.type(), document.id())));
    if (current.isEmpty()) {
      store.delete(namespace, id);
      return;
    }
    var source = current.get(0);
    var metadata = new HashMap<String, String>();
    metadata.put("visibility", "PUBLIC");
    metadata.put("resource_type", source.type());
    metadata.put("resource_id", source.id());
    metadata.put("source_hash", source.hash());
    metadata.put("school_id", source.schoolId());
    if (source.merchantId() != null) metadata.put("merchant_id", source.merchantId());
    if (source.revision() != null) {
      metadata.put("source_version", source.revision().version());
      if (source.revision().validFrom() != null)
        metadata.put("valid_from", source.revision().validFrom().toString());
      if (source.revision().validUntil() != null)
        metadata.put("valid_until", source.revision().validUntil().toString());
    }
    store.upsert(namespace, id, model.embed(source.title() + "\n" + source.body()), metadata);
  }
}
