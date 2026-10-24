package com.campus.perks.user.service;

import com.campus.perks.user.dto.*;
import com.campus.perks.user.vo.*;

public interface AuthenticationService {
  AuthenticationCapabilities capabilities();

  ChallengeView createChallenge(CreateChallengeRequest request, String remoteAddress);

  ChallengeView challenge(String id);

  SessionTokens createSession(CreateSessionRequest request);

  SessionTokens passwordLogin(PasswordLoginRequest request, String remoteAddress);

  SessionTokens register(RegistrationRequest request);

  void resetPassword(PasswordResetRequest request);

  void changePassword(String userId, PasswordChangeRequest request, String remoteAddress);

  SessionTokens refresh(RefreshSessionRequest request);

  void logout(String userId, String sessionId);

  java.util.List<SchoolView> schools();
}
