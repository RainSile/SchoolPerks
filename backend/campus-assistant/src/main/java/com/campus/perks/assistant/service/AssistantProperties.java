package com.campus.perks.assistant.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.assistant")
public record AssistantProperties(
    boolean enabled,
    int historyTurns,
    int requestsPerMinute,
    int maxMessageLength,
    int maxToolRounds,
    int maxToolCalls,
    Duration toolTimeout,
    Duration leaseTtl,
    int retrievalLimit,
    int maxContextChars) {
  public AssistantProperties {
    if (historyTurns < 1
        || historyTurns > 30
        || requestsPerMinute < 1
        || maxMessageLength < 1
        || maxMessageLength > 4000
        || maxToolRounds < 1
        || maxToolRounds > 5
        || maxToolCalls < 1
        || maxToolCalls > 10
        || retrievalLimit < 1
        || retrievalLimit > 20
        || maxContextChars < 1
        || maxContextChars > 20000
        || toolTimeout == null
        || toolTimeout.isNegative()
        || toolTimeout.isZero()
        || leaseTtl == null
        || leaseTtl.isNegative()
        || leaseTtl.isZero()) throw new IllegalArgumentException("Invalid assistant limits");
  }
}
