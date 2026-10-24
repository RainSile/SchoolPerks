package com.campus.perks.notification.serviceImpl;

import com.campus.perks.integration.port.EventPublisherPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class NotificationPublisher implements EventPublisherPort {
  private final NotificationInbox inbox;
  private final NotificationSocket socket;
  private final com.campus.perks.integration.port.SearchProjectionPort search;

  public NotificationPublisher(
      NotificationInbox inbox,
      NotificationSocket socket,
      com.campus.perks.integration.port.SearchProjectionPort search) {
    this.inbox = inbox;
    this.socket = socket;
    this.search = search;
  }

  public void publish(Event event) {
    if ("SEARCH_RESOURCE_CHANGED".equals(event.type())) {
      search.handle(event);
      return;
    }
    var notice = inbox.deliver(event);
    socket.emit(event.payload().get("userId").toString(), notice);
  }
}
