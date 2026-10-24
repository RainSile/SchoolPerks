package com.campus.perks.search.serviceImpl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"campus.auth.enabled", "campus.search.rebuild-enabled"},
    havingValue = "true")
public class SearchRebuildScheduler {
  private final SearchProjectionService projection;

  public SearchRebuildScheduler(SearchProjectionService projection) {
    this.projection = projection;
  }

  @Scheduled(fixedDelayString = "${campus.search.poll-interval-ms}")
  public void dispatch() {
    projection.rebuildOnce();
  }
}
