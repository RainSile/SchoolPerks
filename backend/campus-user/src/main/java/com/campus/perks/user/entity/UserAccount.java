package com.campus.perks.user.entity;

public record UserAccount(
    Long id,
    String phone,
    String nickname,
    Long schoolId,
    String schoolName,
    String status,
    String accountType,
    int accessVersion) {
  @Override
  public String toString() {
    return "UserAccount[REDACTED]";
  }
}
