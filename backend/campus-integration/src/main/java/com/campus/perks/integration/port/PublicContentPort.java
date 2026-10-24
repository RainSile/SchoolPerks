package com.campus.perks.integration.port;

import java.util.List;

/** Public, current canonical content only; no private user/order/qualification data. */
public interface PublicContentPort {
  record Key(String type, String id) {}

  record Content(
      String type,
      String id,
      String schoolId,
      String merchantId,
      String title,
      String body,
      String hash,
      Revision revision) {
    public Content(
        String type,
        String id,
        String schoolId,
        String merchantId,
        String title,
        String body,
        String hash) {
      this(type, id, schoolId, merchantId, title, body, hash, null);
    }
  }

  record Revision(String version, java.time.Instant validFrom, java.time.Instant validUntil) {}

  List<Content> find(List<Key> keys);
}
