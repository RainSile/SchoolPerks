package com.campus.perks.integration.port;

/**
 * Account and school checks stay in the user module. lockActiveUser participates in the caller's
 * transaction.
 */
public interface AccountDirectoryPort {
  void lockActiveUser(String userId);

  void lockExistingUser(String userId);

  void requireSchool(String schoolId);

  void requireVerifiedSchool(String userId, String schoolId);

  void promoteMerchant(String userId);
}
