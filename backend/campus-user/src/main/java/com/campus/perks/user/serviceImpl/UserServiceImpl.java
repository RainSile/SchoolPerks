package com.campus.perks.user.serviceImpl;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.user.dto.*;
import com.campus.perks.user.entity.*;
import com.campus.perks.user.mapper.*;
import com.campus.perks.user.service.*;
import com.campus.perks.user.vo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class UserServiceImpl implements UserService {
  private final UserPermissions permissions;
  private final UserMapper users;
  private final CampusVerificationMapper verifications;
  private final AuthSecrets secrets;
  private final ObjectMapper json;
  private final Clock clock;

  public UserServiceImpl(
      UserMapper users,
      CampusVerificationMapper verifications,
      AuthSecrets secrets,
      ObjectMapper json,
      Clock clock,
      UserPermissions permissions) {
    this.permissions = permissions;
    this.users = users;
    this.verifications = verifications;
    this.secrets = secrets;
    this.json = json;
    this.clock = clock;
  }

  private long id(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,17}"))
      throw new ApplicationException(HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY", "用户身份无效");
    return Long.parseLong(value);
  }

  private UserAccount active(String value, boolean lock) {
    var user = lock ? users.lock(id(value)) : users.find(id(value));
    if (user == null || !"ACTIVE".equals(user.status()))
      throw new ApplicationException(HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY", "用户身份无效");
    return user;
  }

  @Override
  public UserProfile me(String uid) {
    var user = active(uid, false);
    var history = verifications.list(user.id());
    return new UserProfile(
        user.id().toString(),
        user.nickname(),
        mask(user.phone()),
        user.schoolId() == null ? null : user.schoolId().toString(),
        user.schoolName(),
        history.isEmpty() ? "UNVERIFIED" : history.get(0).status(),
        permissions.forUser(user.id()),
        user.accountType(),
        com.campus.perks.security.service.AccountPortal.valueOf(user.accountType()).home());
  }

  private String mask(String phone) {
    return phone.length() < 7
        ? "***"
        : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }

  @Override
  @Transactional
  public UserProfile update(String uid, UpdateProfileRequest request) {
    var user = active(uid, true);
    String nickname = request.nickname().strip();
    if (nickname.isEmpty())
      throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "昵称不能为空");
    users.updateNickname(user.id(), nickname);
    return me(uid);
  }

  @Override
  @Transactional
  public CampusVerificationView apply(String uid, CreateCampusVerificationRequest request) {
    var user = active(uid, true);
    long school = Long.parseLong(request.schoolId());
    if (users.schoolExists(school) != 1)
      throw new ApplicationException(HttpStatus.NOT_FOUND, "SCHOOL_NOT_FOUND", "学校不存在");
    if (verifications.active(user.id()) > 0)
      throw new ApplicationException(HttpStatus.CONFLICT, "VERIFICATION_EXISTS", "已有待审核或通过的校园认证申请");
    String identity;
    try {
      identity =
          json.writeValueAsString(
              Map.of(
                  "realName",
                  request.realName().strip(),
                  "studentNumber",
                  request.studentNumber()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Identity serialization failed");
    }
    String key = UUID.randomUUID().toString();
    verifications.insert(
        key, user.id(), school, secrets.encryptIdentity(uid, identity), clock.instant());
    // Submission alone never grants verified status or changes campus_user.school_id.
    return view(verifications.own(user.id(), key));
  }

  @Override
  public List<CampusVerificationView> verifications(String uid) {
    var user = active(uid, false);
    return verifications.list(user.id()).stream().map(this::view).toList();
  }

  @Override
  public CampusVerificationView verification(String uid, String key) {
    var user = active(uid, false);
    var record = verifications.own(user.id(), key);
    if (record == null)
      throw new ApplicationException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", "校园认证申请不存在");
    return view(record);
  }

  private CampusVerificationView view(CampusVerificationEntity row) {
    return new CampusVerificationView(
        row.id(),
        row.schoolId().toString(),
        row.schoolName(),
        row.status(),
        row.submittedAt(),
        row.reviewedAt(),
        row.reviewReason());
  }
}
