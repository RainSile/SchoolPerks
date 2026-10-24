package com.campus.perks.integration.contract;

import com.campus.perks.integration.port.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class SearchChangeEvents {
  private final OutboxPort outbox;
  private final Clock clock;

  public SearchChangeEvents(OutboxPort outbox, Clock clock) {
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void changed(String type, String id) {
    if (!Set.of("MERCHANT", "COUPON", "OFFER", "POST").contains(type)
        || !id.matches("[1-9][0-9]{0,17}"))
      throw new IllegalArgumentException("Invalid search resource");
    outbox.append(
        new EventPublisherPort.Event(
            UUID.randomUUID().toString(),
            "SEARCH_RESOURCE_CHANGED",
            "SEARCH:" + type + ":" + id,
            clock.instant(),
            Map.of("resourceType", type, "resourceId", id)));
  }
}
