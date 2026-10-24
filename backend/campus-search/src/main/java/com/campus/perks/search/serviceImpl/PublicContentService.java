package com.campus.perks.search.serviceImpl;

import com.campus.perks.integration.port.*;
import com.campus.perks.search.mapper.SearchMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class PublicContentService implements PublicContentPort {
  private final SearchMapper db;
  private final SearchProjectionService projection;

  public PublicContentService(SearchMapper db, SearchProjectionService projection) {
    this.db = db;
    this.projection = projection;
  }

  public List<Content> find(List<Key> keys) {
    if (keys.size() > 100) throw new IllegalArgumentException("Too many public content keys");
    var safe =
        keys.stream()
            .filter(
                k ->
                    java.util.Set.of("MERCHANT", "COUPON", "OFFER", "POST").contains(k.type())
                        && k.id().matches("[1-9][0-9]{0,17}"))
            .map(k -> new SearchEnginePort.Key(k.type(), k.id(), null))
            .toList();
    return db.sources(safe).stream()
        .map(
            s ->
                new Content(
                    s.type(),
                    s.id(),
                    s.schoolId(),
                    s.merchantId(),
                    s.title(),
                    s.body(),
                    projection.hash(s),
                    db.revision(s.type(), s.id())))
        .toList();
  }
}
