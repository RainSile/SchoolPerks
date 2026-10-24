package com.campus.perks.community.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public final class CommunityViews {
  private CommunityViews() {}

  public record Result<T>(boolean created, T value) {}

  public record Review(
      String id,
      String postId,
      int contentVersion,
      JsonNode snapshot,
      String status,
      String reason,
      Instant createdAt,
      Instant reviewedAt) {}

  public record Comment(
      String id, String postId, String body, String status, int version, Instant createdAt) {}

  public record Like(String postId, boolean liked, long count) {}

  public record Report(
      String id,
      String postId,
      String reason,
      String status,
      int version,
      String resolution,
      Instant createdAt) {}

  public record ImageTicket(String fileId, java.net.URI url, Instant expiresAt) {}

  public record Moderation(
      String id,
      String resourceType,
      String resourceId,
      String previousStatus,
      String status,
      String reason,
      Instant createdAt) {}
}
