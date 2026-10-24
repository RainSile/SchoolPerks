package com.campus.perks.notification.serviceImpl;

import com.campus.perks.notification.config.NotificationProperties;
import com.campus.perks.notification.vo.NotificationView;
import com.campus.perks.security.port.SessionStatePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class NotificationSocket extends TextWebSocketHandler {
  private record Client(
      WebSocketSession session,
      Instant connectedAt,
      String userId,
      String sid,
      Instant expiresAt) {}

  private final ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<>();
  private final JwtDecoder decoder;
  private final SessionStatePort sessions;
  private final ObjectMapper json;
  private final Clock clock;
  private final NotificationProperties properties;

  public NotificationSocket(
      JwtDecoder decoder,
      SessionStatePort sessions,
      ObjectMapper json,
      Clock clock,
      NotificationProperties properties) {
    this.decoder = decoder;
    this.sessions = sessions;
    this.json = json;
    this.clock = clock;
    this.properties = properties;
  }

  @Override
  public synchronized void afterConnectionEstablished(WebSocketSession session) throws Exception {
    if (clients.size() >= properties.maxConnections()) {
      session.close(CloseStatus.SERVICE_OVERLOAD);
      return;
    }
    session.setTextMessageSizeLimit(properties.maxMessageBytes());
    var bounded =
        new ConcurrentWebSocketSessionDecorator(
            session, properties.sendTimeLimitMs(), properties.sendBufferBytes());
    clients.put(session.getId(), new Client(bounded, clock.instant(), null, null, null));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    var client = clients.get(session.getId());
    if (client == null) return;
    try {
      var value = json.readTree(message.getPayload());
      if (client.userId() == null) {
        if (!client
            .connectedAt()
            .plus(properties.authenticationTimeout())
            .isAfter(clock.instant())) {
          throw new IllegalArgumentException();
        }
        if (!value.isObject()
            || value.size() != 2
            || !"AUTHENTICATE".equals(value.path("type").asText())
            || !value.path("accessToken").isTextual()) throw new IllegalArgumentException();
        var token = decoder.decode(value.get("accessToken").asText());
        var user = token.getSubject();
        var sid = token.getClaimAsString("sid");
        if (user == null
            || !user.matches("[1-9][0-9]{0,17}")
            || token.getExpiresAt() == null
            || !token.getExpiresAt().isAfter(clock.instant())
            || !sessions.load(user, sid).active()) throw new IllegalArgumentException();
        synchronized (this) {
          if (clients.values().stream().filter(c -> user.equals(c.userId())).count()
              >= properties.maxConnectionsPerUser()) throw new IllegalArgumentException();
          client =
              new Client(client.session(), client.connectedAt(), user, sid, token.getExpiresAt());
          clients.put(session.getId(), client);
        }
        client.session().sendMessage(new TextMessage("{\"type\":\"READY\"}"));
      } else {
        if (!valid(client)
            || !value.isObject()
            || value.size() != 1
            || !"PING".equals(value.path("type").asText())) throw new IllegalArgumentException();
        client.session().sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
      }
    } catch (Exception rejected) {
      close(session.getId());
    }
  }

  private boolean valid(Client client) {
    return client.expiresAt() != null
        && client.expiresAt().isAfter(clock.instant())
        && sessions.load(client.userId(), client.sid()).active();
  }

  @Scheduled(fixedDelayString = "${campus.notifications.poll-interval-ms:2000}")
  public void sweep() {
    for (var entry : clients.entrySet())
      try {
        var c = entry.getValue();
        if (c.userId() == null
            ? !c.connectedAt().plus(properties.authenticationTimeout()).isAfter(clock.instant())
            : !valid(c)) close(entry.getKey());
      } catch (RuntimeException unavailable) {
        close(entry.getKey());
      }
  }

  public void emit(String user, NotificationView event) {
    String payload;
    try {
      payload = json.writeValueAsString(event);
    } catch (Exception invalid) {
      throw new IllegalStateException("Notification serialization failed");
    }
    boolean failed = false;
    for (var entry : clients.entrySet()) {
      var client = entry.getValue();
      if (!user.equals(client.userId())) continue;
      try {
        if (!valid(client)) {
          close(entry.getKey());
          continue;
        }
        client.session().sendMessage(new TextMessage(payload));
      } catch (Exception unavailable) {
        close(entry.getKey());
        failed = true;
      }
    }
    if (failed) throw new IllegalStateException("Notification socket delivery failed");
  }

  private void close(String id) {
    var client = clients.remove(id);
    if (client != null)
      try {
        client.session().close(CloseStatus.POLICY_VIOLATION);
      } catch (Exception ignored) {
      }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    clients.remove(session.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable error) {
    close(session.getId());
  }
}
