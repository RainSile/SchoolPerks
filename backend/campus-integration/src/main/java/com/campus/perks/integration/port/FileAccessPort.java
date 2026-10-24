package com.campus.perks.integration.port;

import java.net.URI;
import java.time.Instant;

/**
 * Trusted intra-server boundary. Controllers must establish actor/resource ownership before
 * delegating.
 */
public interface FileAccessPort {
  void requireReadyOwned(String fileId, String ownerId, String purpose);

  Download signOwnedDownload(String fileId, String ownerId);

  record Download(URI url, Instant expiresAt) {
    @Override
    public String toString() {
      return "Download[REDACTED]";
    }
  }
}
