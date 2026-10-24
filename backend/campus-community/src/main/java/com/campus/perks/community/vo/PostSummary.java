package com.campus.perks.community.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "公开或本人帖子；审核意见仅在本人查询中返回")
public record PostSummary(
    String id,
    String schoolId,
    String merchantId,
    String title,
    String body,
    String status,
    int version,
    java.util.List<String> imageIds,
    long likeCount,
    long commentCount,
    java.time.Instant createdAt,
    java.time.Instant publishedAt,
    String reviewReason) {}
