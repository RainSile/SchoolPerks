package com.campus.perks.user.serviceImpl;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.user.config.AuthProperties;
import com.campus.perks.user.dto.*;
import com.campus.perks.user.service.*;
import com.campus.perks.user.vo.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledUserService implements UserService, AuthenticationService {
  private final AuthProperties properties;
  private final com.campus.perks.user.config.PasswordProperties passwords;

  public DisabledUserService(
      AuthProperties properties, com.campus.perks.user.config.PasswordProperties passwords) {
    this.properties = properties;
    this.passwords = passwords;
  }

  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "认证服务未启用，请配置数据库和认证密钥");
  }

  public AuthenticationCapabilities capabilities() {
    return new AuthenticationCapabilities(
        false,
        false,
        properties.codeLength(),
        properties.resendInterval().toSeconds(),
        false,
        passwords.minLength(),
        passwords.maxBytes());
  }

  public ChallengeView createChallenge(CreateChallengeRequest r, String ip) {
    throw unavailable();
  }

  public ChallengeView challenge(String id) {
    throw unavailable();
  }

  public SessionTokens createSession(CreateSessionRequest r) {
    throw unavailable();
  }

  public SessionTokens passwordLogin(PasswordLoginRequest r, String ip) {
    throw unavailable();
  }

  public SessionTokens register(RegistrationRequest r) {
    throw unavailable();
  }

  public void resetPassword(PasswordResetRequest r) {
    throw unavailable();
  }

  public void changePassword(String id, PasswordChangeRequest r, String ip) {
    throw unavailable();
  }

  public SessionTokens refresh(RefreshSessionRequest r) {
    throw unavailable();
  }

  public void logout(String uid, String sid) {
    throw unavailable();
  }

  public List<SchoolView> schools() {
    throw unavailable();
  }

  public UserProfile me(String uid) {
    throw unavailable();
  }

  public UserProfile update(String uid, UpdateProfileRequest r) {
    throw unavailable();
  }

  public CampusVerificationView apply(String uid, CreateCampusVerificationRequest r) {
    throw unavailable();
  }

  public List<CampusVerificationView> verifications(String uid) {
    throw unavailable();
  }

  public CampusVerificationView verification(String uid, String id) {
    throw unavailable();
  }
}
