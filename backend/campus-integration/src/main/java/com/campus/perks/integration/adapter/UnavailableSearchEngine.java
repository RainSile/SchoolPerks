package com.campus.perks.integration.adapter;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.SearchEnginePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.search.backend", havingValue = "MYSQL", matchIfMissing = true)
public class UnavailableSearchEngine implements SearchEnginePort {
  public boolean available() {
    return false;
  }

  private ApplicationException error() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_ENGINE_UNAVAILABLE", "Elasticsearch + IK 尚未配置");
  }

  public void verifyInstallation() {
    throw error();
  }

  public void index(Document d) {
    throw error();
  }

  public java.util.List<Key> candidates(String keyword, String type, String school, int window) {
    throw error();
  }
}
