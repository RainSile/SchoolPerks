package com.campus.perks.user.serviceImpl;

import com.campus.perks.security.port.SessionStatePort;
import com.campus.perks.user.mapper.*;
import com.campus.perks.user.service.UserPermissions;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class PersistedSessionState implements SessionStatePort {
  private final UserPermissions permissions;
  private final UserMapper users;
  private final SessionMapper sessions;
  private final Clock clock;

  public PersistedSessionState(
      UserMapper users, SessionMapper sessions, Clock clock, UserPermissions permissions) {
    this.permissions = permissions;
    this.users = users;
    this.sessions = sessions;
    this.clock = clock;
  }

  public State load(String userId, String sid) {
    if (sid == null || userId == null || !userId.matches("[1-9][0-9]{0,17}"))
      return new State(false, List.of());
    var session = sessions.find(sid);
    var user = users.find(Long.parseLong(userId));
    if (session == null
        || user == null
        || !session.userId().equals(user.id())
        || session.revokedAt() != null
        || !session.expiresAt().isAfter(clock.instant())
        || !"ACTIVE".equals(user.status())
        || users.currentAccessVersion(user.id(), sid) != 1) return new State(false, List.of());
    return new State(true, permissions.forUser(user.id()), user.accountType());
  }
}
