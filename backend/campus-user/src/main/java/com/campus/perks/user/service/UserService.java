package com.campus.perks.user.service;

import com.campus.perks.user.dto.*;
import com.campus.perks.user.vo.*;
import java.util.List;

public interface UserService {
  UserProfile me(String userId);

  UserProfile update(String userId, UpdateProfileRequest request);

  CampusVerificationView apply(String userId, CreateCampusVerificationRequest request);

  List<CampusVerificationView> verifications(String userId);

  CampusVerificationView verification(String userId, String id);
}
