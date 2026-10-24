package com.campus.perks.user.serviceImpl;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.AccountDirectoryPort;
import com.campus.perks.user.mapper.UserMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AccountDirectoryService implements AccountDirectoryPort {
  private final UserMapper users;

  public AccountDirectoryService(UserMapper users) {
    this.users = users;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockActiveUser(String id) {
    var user = users.lock(Long.parseLong(id));
    if (user == null || !"ACTIVE".equals(user.status()))
      throw new ApplicationException(HttpStatus.CONFLICT, "ACCOUNT_UNAVAILABLE", "目标账号不可用");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockExistingUser(String id) {
    if (users.lock(Long.parseLong(id)) == null)
      throw new ApplicationException(HttpStatus.CONFLICT, "ACCOUNT_UNAVAILABLE", "目标账号不可用");
  }

  public void requireVerifiedSchool(String userId, String schoolId) {
    var user = users.find(Long.parseLong(userId));
    if (user == null
        || user.schoolId() == null
        || !user.schoolId().toString().equals(schoolId)
        || users.verifiedSchool(Long.parseLong(userId), Long.parseLong(schoolId)) != 1)
      throw new ApplicationException(
          HttpStatus.FORBIDDEN, "CAMPUS_VERIFICATION_REQUIRED", "此优惠仅限本校已认证用户领取");
  }

  public void requireSchool(String id) {
    if (users.schoolExists(Long.parseLong(id)) != 1)
      throw new ApplicationException(HttpStatus.NOT_FOUND, "SCHOOL_NOT_FOUND", "学校不存在");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void promoteMerchant(String id) {
    var user = users.lock(Long.parseLong(id));
    if (user == null || !"ACTIVE".equals(user.status()) || "ADMIN".equals(user.accountType()))
      throw new ApplicationException(
          HttpStatus.CONFLICT, "ACCOUNT_PORTAL_CONFLICT", "平台管理账号不能成为商家成员");
    if ("USER".equals(user.accountType())) {
      users.promoteMerchant(user.id());
      users.revokeSessions(user.id());
    }
  }
}
