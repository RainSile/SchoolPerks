package com.campus.perks.integration.adapter;

import static com.mongodb.client.model.Filters.*;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.InfrastructureProperties;
import com.campus.perks.integration.port.ConversationStorePort;
import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.infrastructure.mongo.enabled", havingValue = "true")
@EnableConfigurationProperties(InfrastructureProperties.class)
public class MongoConversationStore implements ConversationStorePort, AutoCloseable {
  private final MongoClient client;
  private final MongoCollection<Document> turns;
  private volatile boolean indexed;

  public MongoConversationStore(InfrastructureProperties config) {
    var p = config.mongo();
    long timeout = p.timeout().toMillis();
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(p.uri()))
                .applyToClusterSettings(
                    b -> b.serverSelectionTimeout(timeout, TimeUnit.MILLISECONDS))
                .applyToConnectionPoolSettings(
                    b -> b.maxSize(3).minSize(0).maxWaitTime(timeout, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(
                    b ->
                        b.connectTimeout((int) timeout, TimeUnit.MILLISECONDS)
                            .readTimeout((int) timeout, TimeUnit.MILLISECONDS))
                .build());
    turns = client.getDatabase(p.database()).getCollection("assistant_turns");
  }

  private synchronized void indexes() {
    if (!indexed) {
      turns.createIndex(
          Indexes.compoundIndex(
              Indexes.ascending("owner", "portal", "conversationId"),
              Indexes.descending("createdAt")));
      indexed = true;
    }
  }

  private ApplicationException failure() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "CONVERSATION_STORE_UNAVAILABLE", "会话存储暂不可用");
  }

  private Turn turn(Document d) {
    var sources =
        d.getList("sources", Document.class, List.of()).stream()
            .map(
                s ->
                    new Source(
                        s.getString("type"),
                        s.getString("id"),
                        s.getString("title"),
                        s.getString("excerpt"),
                        s.getString("hash")))
            .toList();
    return new Turn(
        d.getString("_id"),
        d.getString("conversationId"),
        d.getString("owner"),
        d.getString("portal"),
        d.getString("question"),
        d.getString("answer"),
        d.getList("tools", String.class, List.of()),
        sources,
        d.getDate("createdAt").toInstant(),
        d.getInteger("attempt", 0));
  }

  public Turn completed(String id, String owner, String portal) {
    try {
      var d = turns.find(and(eq("_id", id), eq("owner", owner), eq("portal", portal))).first();
      return d == null ? null : turn(d);
    } catch (Exception e) {
      throw failure();
    }
  }

  public List<Turn> history(String conversation, String owner, String portal, int limit) {
    try {
      indexes();
      var rows =
          turns
              .find(
                  and(eq("conversationId", conversation), eq("owner", owner), eq("portal", portal)))
              .sort(Sorts.descending("createdAt"))
              .limit(limit)
              .map(this::turn)
              .into(new ArrayList<>());
      Collections.reverse(rows);
      return rows;
    } catch (Exception e) {
      throw failure();
    }
  }

  public void save(Turn t) {
    try {
      indexes();
      var sources =
          t.sources().stream()
              .map(
                  s ->
                      new Document("type", s.type())
                          .append("id", s.id())
                          .append("title", s.title())
                          .append("excerpt", s.excerpt())
                          .append("hash", s.hash()))
              .toList();
      var d =
          new Document("_id", t.id())
              .append("conversationId", t.conversationId())
              .append("owner", t.owner())
              .append("portal", t.portal())
              .append("question", t.question())
              .append("answer", t.answer())
              .append("tools", t.tools())
              .append("sources", sources)
              .append("createdAt", Date.from(t.createdAt()))
              .append("attempt", t.attempt());
      turns.replaceOne(
          and(
              eq("_id", t.id()),
              eq("owner", t.owner()),
              eq("portal", t.portal()),
              or(exists("attempt", false), lte("attempt", t.attempt()))),
          d,
          new ReplaceOptions().upsert(true));
    } catch (com.mongodb.MongoWriteException e) {
      if (e.getError().getCode() != 11000) throw failure();
    } catch (Exception e) {
      throw failure();
    }
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    client.close();
  }
}
