package com.campus.perks.search.serviceImpl;

import com.campus.perks.integration.port.*;
import com.campus.perks.integration.port.SearchEnginePort.*;
import com.campus.perks.search.entity.SearchSource;
import com.campus.perks.search.mapper.SearchMapper;
import com.campus.perks.search.service.SearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
@EnableConfigurationProperties(SearchProperties.class)
public class SearchProjectionService implements SearchProjectionPort {
  private final SearchMapper db;
  private final SearchEnginePort engine;
  private final SearchProperties config;
  private final Clock clock;
  private final ObjectMapper json;
  private final org.springframework.beans.factory.ObjectProvider<VectorProjectionPort> vectors;
  private final TransactionTemplate tx;

  public SearchProjectionService(
      SearchMapper db,
      SearchEnginePort engine,
      SearchProperties config,
      Clock clock,
      ObjectMapper json,
      PlatformTransactionManager manager,
      org.springframework.beans.factory.ObjectProvider<VectorProjectionPort> vectors) {
    this.db = db;
    this.engine = engine;
    this.config = config;
    this.clock = clock;
    this.json = json;
    this.vectors = vectors;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public String hash(SearchSource source) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(source)));
    } catch (Exception e) {
      throw new IllegalStateException("Search hashing failed");
    }
  }

  public void handle(EventPublisherPort.Event event) {
    String type = Objects.toString(event.payload().get("resourceType"), ""),
        id = Objects.toString(event.payload().get("resourceId"), "");
    if (!"SEARCH_RESOURCE_CHANGED".equals(event.type())
        || !Set.of("MERCHANT", "COUPON", "OFFER", "POST").contains(type)
        || !id.matches("[1-9][0-9]{0,17}"))
      throw new IllegalArgumentException("Unsupported search event");
    if (db.consumed(event.eventId())) return;
    Document plan =
        tx.execute(
            status -> {
              long revision = db.lockDocument(type, id, clock.instant()) + 1;
              var source = db.source(type, id);
              var d =
                  source == null
                      ? new Document(type, id, null, null, "", "", null, false, revision)
                      : new Document(
                          type,
                          id,
                          source.schoolId(),
                          source.merchantId(),
                          source.title(),
                          source.body(),
                          hash(source),
                          true,
                          revision);
              db.update(d, clock.instant());
              return d;
            });
    // Tombstones remain documents with visible=false, so old versions cannot resurrect hidden text.
    if ("ELASTICSEARCH".equals(config.backend())) engine.index(plan);
    var vector = vectors.getIfAvailable();
    if (vector != null) vector.project(plan);
    tx.executeWithoutResult(status -> db.consume(event.eventId(), clock.instant()));
  }

  public SearchMapper.Job lease() {
    return tx.execute(
        s ->
            db.lease(
                clock.instant(),
                clock.instant().plus(config.leaseTtl()),
                UUID.randomUUID().toString()));
  }

  public void rebuildOnce() {
    var job = lease();
    if (job == null) return;
    try {
      var keys = db.keys(job.value().cursorType(), job.value().cursorId(), config.batchSize());
      for (var key : keys) {
        String eventId =
            UUID.nameUUIDFromBytes(
                    (job.value().id() + ":" + key.type() + ":" + key.id())
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
        handle(
            new EventPublisherPort.Event(
                eventId,
                "SEARCH_RESOURCE_CHANGED",
                "SEARCH:" + key.type() + ":" + key.id(),
                clock.instant(),
                Map.of("resourceType", key.type(), "resourceId", key.id())));
      }
      tx.executeWithoutResult(
          s ->
              db.advance(
                  job,
                  keys.isEmpty() ? null : keys.get(keys.size() - 1),
                  keys.size(),
                  keys.size() < config.batchSize(),
                  clock.instant()));
    } catch (RuntimeException e) {
      tx.executeWithoutResult(s -> db.fail(job));
    }
  }
}
