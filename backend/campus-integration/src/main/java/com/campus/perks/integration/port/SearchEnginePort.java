package com.campus.perks.integration.port;

import java.util.List;

/**
 * External search only supplies candidates. Domain visibility/content must be rechecked in MySQL.
 */
public interface SearchEnginePort {
  boolean available();

  void verifyInstallation();

  void index(Document document);

  List<Key> candidates(String keyword, String type, String schoolId, int window);

  record Key(String type, String id, String sourceHash) {}

  record Document(
      String type,
      String id,
      String schoolId,
      String merchantId,
      String title,
      String body,
      String sourceHash,
      boolean visible,
      long revision) {}
}
