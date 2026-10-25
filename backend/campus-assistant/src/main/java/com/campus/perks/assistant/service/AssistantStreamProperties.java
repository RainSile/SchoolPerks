package com.campus.perks.assistant.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("campus.assistant-stream")
public record AssistantStreamProperties(
    @DefaultValue("4") int workers,
    @DefaultValue("16") int queueCapacity,
    @DefaultValue("65536") int maxAnswerChars) {
  public AssistantStreamProperties {
    if (workers < 1
        || workers > 32
        || queueCapacity < 1
        || queueCapacity > 256
        || maxAnswerChars < 4000
        || maxAnswerChars > 1000000)
      throw new IllegalArgumentException("Invalid assistant stream limits");
  }
}
