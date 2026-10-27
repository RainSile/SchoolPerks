package com.campus.perks.integration.port;

import java.net.URI;
import java.time.Duration;

/**
 * Provider is absent by default. Implementations must use private storage and bounded I/O timeouts.
 */
public interface ObjectStoragePort {
  boolean available();

  URI createUploadUrl(String stagingKey, String contentType, long size, Duration expiry);

  /** Copy staging bytes to a new immutable key, then inspect the copy, not the mutable source. */
  StoredObject seal(String stagingKey, String immutableKey);

  URI createDownloadUrl(String immutableKey, Duration expiry);

  void delete(String objectKey);

  record StoredObject(long size, String contentType, byte[] prefix) {
    @Override
    public String toString() {
      return "StoredObject[REDACTED]";
    }
  }
}
