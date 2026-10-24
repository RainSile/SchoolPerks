package com.campus.perks.community.service;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.community")
public record CommunityProperties(
    int maxImages, int maxBodyLength, int maxCommentLength, Set<String> blockedTerms) {
  public CommunityProperties {
    if (maxImages < 0
        || maxImages > 12
        || maxBodyLength < 1
        || maxBodyLength > 6000
        || maxCommentLength < 1
        || maxCommentLength > 2000) throw new IllegalArgumentException("Invalid community limits");
    blockedTerms = blockedTerms == null ? Set.of() : Set.copyOf(blockedTerms);
  }
}
