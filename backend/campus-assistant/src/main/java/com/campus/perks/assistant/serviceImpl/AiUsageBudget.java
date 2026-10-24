package com.campus.perks.assistant.serviceImpl;

import com.campus.perks.assistant.mapper.AiUsageMapper;
import com.campus.perks.assistant.service.AiUsageProperties;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.AccountDirectoryPort;
import com.campus.perks.security.service.CurrentActor;
import java.time.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Conservative per-account UTC-day call budget. Failed provider attempts still count. */
@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
@EnableConfigurationProperties(AiUsageProperties.class)
public class AiUsageBudget {
  private final AiUsageMapper db;
  private final AiUsageProperties p;
  private final AccountDirectoryPort accounts;
  private final Clock clock;
  private final TransactionTemplate tx;

  public AiUsageBudget(
      AiUsageMapper db,
      AiUsageProperties p,
      AccountDirectoryPort accounts,
      Clock clock,
      PlatformTransactionManager manager) {
    this.db = db;
    this.p = p;
    this.accounts = accounts;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void reserve(String kind) {
    int maximum =
        switch (kind) {
          case "CHAT" -> p.dailyChatCalls();
          case "EMBEDDING" -> p.dailyEmbeddingCalls();
          default -> throw new IllegalArgumentException("Unknown AI usage kind");
        };
    tx.executeWithoutResult(
        s -> {
          String actor = CurrentActor.id();
          accounts.lockActiveUser(actor);
          if (!db.reserve(
              actor, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), kind, maximum))
            throw new ApplicationException(
                HttpStatus.TOO_MANY_REQUESTS, "AI_DAILY_BUDGET_EXCEEDED", "今日模型调用额度已用完，请明日再试");
        });
  }

  public int used(String kind) {
    return db.used(CurrentActor.id(), LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), kind);
  }
}
