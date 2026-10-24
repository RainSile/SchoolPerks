package com.campus.perks.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主库复核后的公开搜索结果；高亮为纯文本片段，不含HTML")
public record SearchHit(
    String id,
    String resourceType,
    String schoolId,
    String merchantId,
    String title,
    String excerpt,
    java.util.List<Segment> titleSegments,
    java.util.List<Segment> excerptSegments) {
  public record Segment(String text, boolean matched) {}
}
