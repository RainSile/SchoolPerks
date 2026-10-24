package com.campus.perks.assistant.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.assistant.budget")
public record AiUsageProperties(int dailyChatCalls, int dailyEmbeddingCalls) {
  public AiUsageProperties {
    if (dailyChatCalls < 1 || dailyEmbeddingCalls < 1)
      throw new IllegalArgumentException("AI budgets must be positive");
  }
}
