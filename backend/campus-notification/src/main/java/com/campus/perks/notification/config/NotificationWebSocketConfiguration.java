package com.campus.perks.notification.config;

import com.campus.perks.notification.serviceImpl.NotificationSocket;
import com.campus.perks.security.config.CampusSecurityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class NotificationWebSocketConfiguration implements WebSocketConfigurer {
  private final NotificationSocket socket;
  private final CampusSecurityProperties security;

  public NotificationWebSocketConfiguration(
      NotificationSocket socket, CampusSecurityProperties security) {
    this.socket = socket;
    this.security = security;
  }

  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(socket, "/api/v1/notification-stream")
        .setAllowedOrigins(security.allowedOrigins().toArray(String[]::new));
  }
}
