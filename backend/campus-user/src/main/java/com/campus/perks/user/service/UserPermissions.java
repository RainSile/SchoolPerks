package com.campus.perks.user.service;

import com.campus.perks.security.port.PermissionContributor;
import com.campus.perks.user.mapper.UserMapper;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class UserPermissions {
  private final UserMapper users;
  private final ObjectProvider<PermissionContributor> contributors;

  public UserPermissions(UserMapper users, ObjectProvider<PermissionContributor> contributors) {
    this.users = users;
    this.contributors = contributors;
  }

  public List<String> forUser(long userId) {
    var user = users.find(userId);
    if (user == null) return List.of();
    var result = new TreeSet<String>(users.permissions(userId));
    if ("MERCHANT".equals(user.accountType()))
      contributors
          .orderedStream()
          .forEach(p -> result.addAll(p.permissions(Long.toString(userId))));
    return List.copyOf(result);
  }
}
