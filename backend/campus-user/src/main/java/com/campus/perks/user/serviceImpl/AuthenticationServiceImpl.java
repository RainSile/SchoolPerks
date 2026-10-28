package com.campus.perks.user.serviceImpl;

import com.campus.perks.common.config.BusinessProperties;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.SmsSenderPort;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.campus.perks.user.config.AuthProperties;
import com.campus.perks.user.config.PasswordProperties;
import com.campus.perks.user.dto.*;
import com.campus.perks.user.entity.*;
import com.campus.perks.user.mapper.*;
import com.campus.perks.user.service.*;
import com.campus.perks.user.vo.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AuthenticationServiceImpl implements AuthenticationService {
  private final ChallengeMapper challenges;
  private final SessionMapper sessions;
  private final UserPermissions permissions;
  private final UserMapper users;
  private final SmsSenderPort sms;
  private final AuthSecrets secrets;
  private final AccessTokenIssuer issuer;
  private final AuthProperties properties;
  private final BusinessProperties business;
  private final Clock clock;
  private final PasswordMapper passwords;
  private final PasswordProperties passwordPolicy;
  private final BCryptPasswordEncoder encoder;
  private final String dummyHash;

  public AuthenticationServiceImpl(
      ChallengeMapper challenges,
      SessionMapper sessions,
      UserMapper users,
      SmsSenderPort sms,
      AuthSecrets secrets,
      AccessTokenIssuer issuer,
      AuthProperties properties,
      BusinessProperties business,
      Clock clock,
      UserPermissions permissions,
      PasswordMapper passwords,
      PasswordProperties passwordPolicy) {
    this.passwords = passwords;
    this.passwordPolicy = passwordPolicy;
    this.encoder = new BCryptPasswordEncoder(passwordPolicy.bcryptStrength());
    this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    if (sms.simulated()) {
      if (sms.codeOverride() == null || sms.codeOverride().length() != properties.codeLength())
        throw new IllegalStateException("Simulated SMS code length must match SMS_CODE_LENGTH");
    } else if (properties.codeLength() < 6) {
      throw new IllegalStateException("Real SMS codes require at least six digits");
    }
    this.permissions = permissions;
    this.challenges = challenges;
    this.sessions = sessions;
    this.users = users;
    this.sms = sms;
    this.secrets = secrets;
    this.issuer = issuer;
    this.properties = properties;
    this.business = business;
    this.clock = clock;
    if (business.verificationCodeTtl().isNegative() || business.verificationCodeTtl().isZero())
      throw new IllegalStateException("Verification TTL must be positive");
  }

  @Override
  public AuthenticationCapabilities capabilities() {
    return new AuthenticationCapabilities(
        true,
        sms.available(),
        properties.codeLength(),
        properties.resendInterval().toSeconds(),
        sms.simulated(),
        passwordPolicy.minLength(),
        passwordPolicy.maxBytes());
  }

  // Commit attempts/rate counters and FAILED state when returning a deliberate client error.
  // Unexpected database/crypto failures still roll back normally.
  @Override
  @Transactional(noRollbackFor = ApplicationException.class)
  public ChallengeView createChallenge(CreateChallengeRequest request, String ip) {
    if (!sms.available())
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "SMS_UNAVAILABLE", "短信服务尚未配置");
    Instant now = clock.instant();
    rate("phone-cooldown:" + request.phone(), properties.resendInterval(), 1, now);
    rate(
        "phone-window:" + request.phone(),
        properties.rateWindow(),
        properties.phoneWindowLimit(),
        now);
    rate("ip-window:" + ip, properties.rateWindow(), properties.ipWindowLimit(), now);
    String id = UUID.randomUUID().toString();
    boolean remote = sms.verifiesRemotely();
    String verifier = remote ? sms.verifierType() : "LOCAL";
    if (remote
        && (verifier == null || !verifier.matches("[A-Z_]{1,32}") || "LOCAL".equals(verifier)))
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "SMS_UNAVAILABLE", "短信核验服务配置不可用");
    String code =
        remote
            ? null
            : sms.simulated() ? sms.codeOverride() : secrets.code(properties.codeLength());
    Instant expires = now.plus(business.verificationCodeTtl());
    challenges.invalidatePrevious(request.phone(), request.purpose());
    challenges.insert(
        new ChallengeEntity(
            id,
            request.phone(),
            request.purpose(),
            remote ? null : secrets.hash(id + ":" + code),
            0,
            "SENT",
            now,
            expires,
            verifier,
            null,
            null));
    try {
      if (remote) {
        var receipt =
            sms.startRemoteVerification(
                new SmsSenderPort.RemoteChallenge(
                    id,
                    request.phone(),
                    request.purpose(),
                    properties.codeLength(),
                    business.verificationCodeTtl(),
                    properties.resendInterval()));
        if (receipt == null
            || !verifier.equals(receipt.verifierType())
            || receipt.reference() == null
            || receipt.reference().isBlank()
            || receipt.scope() == null
            || receipt.scope().isBlank())
          throw error(HttpStatus.SERVICE_UNAVAILABLE, "SMS_DELIVERY_FAILED", "验证码发送失败，请稍后重试");
        challenges.delivery(id, receipt.reference(), receipt.scope());
      } else {
        sms.sendVerificationCode(
            request.phone(), request.purpose(), code, business.verificationCodeTtl());
      }
    } catch (RuntimeException ex) {
      challenges.status(id, "FAILED");
      if (ex instanceof ApplicationException application) throw application;
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "SMS_DELIVERY_FAILED", "验证码发送失败，请稍后重试");
    }
    return new ChallengeView(id, expires, "SENT");
  }

  private void rate(String key, Duration window, int maximum, Instant now) {
    String hash = secrets.hash("rate:" + key);
    challenges.ensureRate(hash, now);
    var bucket = challenges.lockRate(hash);
    boolean reset = !bucket.windowStart().plus(window).isAfter(now);
    int count = reset ? 0 : bucket.requestCount();
    if (count >= maximum)
      throw error(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_RATE_LIMITED", "认证操作过于频繁，请稍后重试");
    challenges.setRate(hash, count + 1, reset ? now : bucket.windowStart());
  }

  @Override
  public ChallengeView challenge(String id) {
    var challenge = challenges.find(id);
    if (challenge == null) throw error(HttpStatus.NOT_FOUND, "CHALLENGE_NOT_FOUND", "验证码请求不存在");
    String status = challenge.status();
    if ("SENT".equals(status) && !challenge.expiresAt().isAfter(clock.instant()))
      status = "EXPIRED";
    if ("SENT".equals(status) && challenge.attempts() >= properties.maxCodeAttempts())
      status = "LOCKED";
    return new ChallengeView(challenge.id(), challenge.expiresAt(), status);
  }

  @Override
  @Transactional(noRollbackFor = ApplicationException.class)
  public SessionTokens createSession(CreateSessionRequest request) {
    Instant now = clock.instant();
    consumeCode(request.phone(), request.challengeId(), request.code(), "LOGIN", now);
    users.register(
        request.phone(),
        properties.nicknamePrefix() + UUID.randomUUID().toString().substring(0, 6));
    var user = users.lockByPhone(request.phone());
    if (!"ACTIVE".equals(user.status())) throw invalidSession();
    return newSession(user, now);
  }

  private SessionTokens newSession(UserAccount user, Instant now) {
    var session =
        new SessionEntity(
            UUID.randomUUID().toString(),
            user.id(),
            now,
            now.plus(properties.refreshTokenTtl()),
            null);
    sessions.insert(session);
    return tokens(user, session, now);
  }

  private void consumeCode(String phone, String id, String code, String purpose, Instant now) {
    var challenge = challenges.lock(id);
    if (challenge == null
        || !"SENT".equals(challenge.status())
        || !purpose.equals(challenge.purpose())
        || !challenge.expiresAt().isAfter(now)
        || challenge.attempts() >= properties.maxCodeAttempts()) throw invalidCode();
    if (!phone.equals(challenge.phone())) {
      challenges.failedAttempt(id);
      throw invalidCode();
    }
    boolean matches;
    if ("LOCAL".equals(challenge.verifierType())) {
      if (sms.verifiesRemotely())
        throw error(
            HttpStatus.SERVICE_UNAVAILABLE, "SMS_VERIFIER_UNAVAILABLE", "原短信核验服务暂不可用，请重新获取验证码");
      matches = secrets.matches(challenge.codeHash(), id + ":" + code);
    } else {
      if (!sms.available()
          || !sms.verifiesRemotely()
          || !challenge.verifierType().equals(sms.verifierType()))
        throw error(
            HttpStatus.SERVICE_UNAVAILABLE, "SMS_VERIFIER_UNAVAILABLE", "原短信核验服务暂不可用，请稍后重试");
      matches =
          sms.verifyRemoteCode(
              phone, purpose, challenge.providerScope(), challenge.providerReference(), id, code);
    }
    if (!matches) {
      challenges.failedAttempt(id);
      throw invalidCode();
    }
    challenges.status(id, "CONSUMED");
  }

  private void validatePassword(String password) {
    if (password.codePointCount(0, password.length()) < passwordPolicy.minLength()
        || password.getBytes(StandardCharsets.UTF_8).length > passwordPolicy.maxBytes())
      throw error(
          HttpStatus.BAD_REQUEST,
          "PASSWORD_POLICY",
          "密码至少 "
              + passwordPolicy.minLength()
              + " 个字符，UTF-8 编码不超过 "
              + passwordPolicy.maxBytes()
              + " 字节");
  }

  private boolean matchesPassword(String password, String hash) {
    boolean valid = password.getBytes(StandardCharsets.UTF_8).length <= passwordPolicy.maxBytes();
    boolean matches = encoder.matches(valid ? password : "", hash == null ? dummyHash : hash);
    return valid && hash != null && matches;
  }

  private void passwordRate(String phone, String ip, Instant now) {
    rate("password-phone:" + phone, passwordPolicy.rateWindow(), passwordPolicy.phoneLimit(), now);
    rate("password-ip:" + ip, passwordPolicy.rateWindow(), passwordPolicy.ipLimit(), now);
  }

  private ApplicationException invalidPassword() {
    return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "手机号或密码错误，或账号不可用");
  }

  @Override
  @Transactional(noRollbackFor = ApplicationException.class)
  public SessionTokens passwordLogin(PasswordLoginRequest request, String ip) {
    Instant now = clock.instant();
    passwordRate(request.phone(), ip, now);
    var user = users.lockByPhone(request.phone());
    boolean matches =
        matchesPassword(request.password(), user == null ? null : passwords.hash(user.id()));
    if (user == null || !matches || !"ACTIVE".equals(user.status())) throw invalidPassword();
    return newSession(user, now);
  }

  @Override
  @Transactional(noRollbackFor = ApplicationException.class)
  public SessionTokens register(RegistrationRequest request) {
    validatePassword(request.password());
    Instant now = clock.instant();
    consumeCode(request.phone(), request.challengeId(), request.code(), "REGISTER", now);
    if (users.lockByPhone(request.phone()) != null)
      throw error(HttpStatus.CONFLICT, "PHONE_REGISTERED", "该手机号已注册，请登录或找回密码");
    passwords.register(
        request.phone(), request.nickname().strip(), encoder.encode(request.password()));
    return newSession(users.lockByPhone(request.phone()), now);
  }

  @Override
  @Transactional(noRollbackFor = ApplicationException.class)
  public void resetPassword(PasswordResetRequest request) {
    validatePassword(request.password());
    consumeCode(
        request.phone(), request.challengeId(), request.code(), "RESET_PASSWORD", clock.instant());
    var user = users.lockByPhone(request.phone());
    if (user == null || !"ACTIVE".equals(user.status())) throw invalidPassword();
    passwords.update(user.id(), encoder.encode(request.password()));
    users.revokeSessions(user.id());
  }

  @Override
  @Transactional(noRollbackFor = ApplicationException.class)
  public void changePassword(String userId, PasswordChangeRequest request, String ip) {
    validatePassword(request.password());
    passwordRate(userId, ip, clock.instant());
    var user = users.lock(Long.parseLong(userId));
    if (user == null
        || !matchesPassword(request.currentPassword(), passwords.hash(user.id()))
        || !"ACTIVE".equals(user.status())) throw invalidPassword();
    passwords.update(user.id(), encoder.encode(request.password()));
    users.revokeSessions(user.id());
  }

  @Override
  @Transactional(noRollbackFor = ApplicationException.class)
  public SessionTokens refresh(RefreshSessionRequest request) {
    String hash = secrets.hash("refresh:" + request.refreshToken());
    var initial = sessions.token(hash);
    if (initial == null) throw invalidSession();
    var initialSession = sessions.find(initial.sessionId());
    if (initialSession == null) throw invalidSession();
    // Acquire the account before session rows, matching password changes/resets.
    var user = users.lock(initialSession.userId());
    var session = sessions.lock(initial.sessionId());
    var refresh = sessions.lockToken(hash);
    Instant now = clock.instant();
    if (session == null
        || session.revokedAt() != null
        || !session.expiresAt().isAfter(now)
        || refresh == null
        || !refresh.expiresAt().isAfter(now)) throw invalidSession();
    if (refresh.consumedAt() != null) {
      sessions.revoke(
          session.id(), now); // Reuse revokes the complete family, including newly issued tokens.
      throw invalidSession();
    }
    if (user == null
        || !"ACTIVE".equals(user.status())
        || users.currentAccessVersion(user.id(), session.id()) != 1) {
      sessions.revoke(session.id(), now);
      throw invalidSession();
    }
    if (sessions.consume(hash, now) != 1) throw invalidSession();
    return tokens(user, session, now);
  }

  private SessionTokens tokens(UserAccount user, SessionEntity session, Instant now) {
    String refresh = secrets.token();
    sessions.insertToken(secrets.hash("refresh:" + refresh), session.id(), session.expiresAt());
    Instant accessExpires = now.plus(properties.accessTokenTtl());
    if (accessExpires.isAfter(session.expiresAt())) accessExpires = session.expiresAt();
    return new SessionTokens(
        session.id(),
        "Bearer",
        issuer.issue(
            user.id().toString(), session.id(), permissions.forUser(user.id()), now, accessExpires),
        accessExpires,
        refresh,
        session.expiresAt());
  }

  @Override
  @Transactional
  public void logout(String userId, String sid) {
    if (sid == null) throw invalidSession();
    var session = sessions.lock(sid);
    if (session == null || !session.userId().toString().equals(userId)) throw invalidSession();
    sessions.revoke(sid, clock.instant());
  }

  @Override
  public List<SchoolView> schools() {
    return users.schools();
  }

  private ApplicationException invalidCode() {
    return error(HttpStatus.UNAUTHORIZED, "INVALID_VERIFICATION", "验证码无效、已过期或已使用");
  }

  private ApplicationException invalidSession() {
    return error(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "会话无效，请重新登录");
  }

  private ApplicationException error(HttpStatus status, String code, String detail) {
    return new ApplicationException(status, code, detail);
  }
}
