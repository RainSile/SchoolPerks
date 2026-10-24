package com.campus.perks.community.entity;

import java.time.Instant;

public final class CommunityRows {
  private CommunityRows() {}

  public record Post(
      long id,
      long authorId,
      long schoolId,
      Long merchantId,
      String title,
      String body,
      String status,
      int version,
      Instant createdAt,
      Instant publishedAt) {}

  public record Review(
      String id,
      long postId,
      int contentVersion,
      String snapshot,
      String status,
      Long reviewerId,
      String reason,
      Instant createdAt,
      Instant reviewedAt) {}

  public record Comment(
      long id,
      long postId,
      long authorId,
      String requestKey,
      String body,
      String status,
      int version,
      Instant createdAt) {}

  public record Report(
      long id,
      long postId,
      long reporterId,
      String reason,
      String status,
      int version,
      String resolution,
      Instant createdAt) {}
}
