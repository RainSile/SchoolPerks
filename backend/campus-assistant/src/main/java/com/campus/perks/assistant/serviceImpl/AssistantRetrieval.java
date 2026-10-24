package com.campus.perks.assistant.serviceImpl;

import com.campus.perks.assistant.service.AssistantProperties;
import com.campus.perks.integration.config.IntegrationProperties;
import com.campus.perks.integration.port.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AssistantRetrieval {
  private final ObjectProvider<AiModelPort> model;
  private final ObjectProvider<VectorSearchPort> vectors;
  private final PublicContentPort content;
  private final IntegrationProperties integrations;
  private final AssistantProperties p;
  private final AiUsageBudget budget;

  public AssistantRetrieval(
      ObjectProvider<AiModelPort> model,
      ObjectProvider<VectorSearchPort> vectors,
      PublicContentPort content,
      IntegrationProperties integrations,
      AssistantProperties p,
      AiUsageBudget budget) {
    this.budget = budget;
    this.model = model;
    this.vectors = vectors;
    this.content = content;
    this.integrations = integrations;
    this.p = p;
  }

  public List<ConversationStorePort.Source> retrieve(String question) {
    var v = vectors.getIfAvailable();
    var m = model.getIfAvailable();
    if (v == null || m == null) return List.of();
    budget.reserve("EMBEDDING");
    List<VectorSearchPort.Match> hits;
    try {
      hits = v.search(integrations.pinecone().namespace(), m.embed(question), p.retrievalLimit());
    } catch (com.campus.perks.common.exception.ApplicationException unavailable) {
      if (unavailable.status().is5xxServerError()) return List.of();
      throw unavailable;
    }
    var keys = new ArrayList<PublicContentPort.Key>();
    for (var hit : hits) {
      String type = hit.metadata().get("resource_type"), id = hit.metadata().get("resource_id");
      if (type != null && id != null && hit.documentId().equals(type + ":" + id))
        keys.add(new PublicContentPort.Key(type, id));
    }
    var current = new HashMap<String, PublicContentPort.Content>();
    content.find(keys).forEach(d -> current.put(d.type() + ":" + d.id(), d));
    var result = new ArrayList<ConversationStorePort.Source>();
    int remaining = p.maxContextChars();
    for (var hit : hits) {
      var d = current.remove(hit.documentId());
      if (d == null || !d.hash().equals(hit.metadata().get("source_hash"))) continue;
      if (d.revision() != null
          && hit.metadata().containsKey("source_version")
          && !d.revision().version().equals(hit.metadata().get("source_version"))) continue;
      String excerpt =
          d.body()
              .substring(
                  0,
                  Math.min(
                      d.body().length(),
                      Math.min(remaining, p.maxContextChars() / p.retrievalLimit())));
      remaining -= excerpt.length();
      result.add(new ConversationStorePort.Source(d.type(), d.id(), d.title(), excerpt, d.hash()));
      if (remaining <= 0) break;
    }
    return result;
  }

  public boolean current(List<ConversationStorePort.Source> sources) {
    if (sources.isEmpty()) return true;
    var current = new HashMap<String, String>();
    content
        .find(sources.stream().map(s -> new PublicContentPort.Key(s.type(), s.id())).toList())
        .forEach(s -> current.put(s.type() + ":" + s.id(), s.hash()));
    return sources.stream()
        .allMatch(s -> s.hash() != null && s.hash().equals(current.get(s.type() + ":" + s.id())));
  }
}
