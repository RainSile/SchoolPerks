package com.campus.perks.integration.port;

/** Public community identity only; never exposes phone, permissions or verification evidence. */
public interface PublicUserPort {
  Profile profile(String userId);
  record Profile(String id, String nickname, String avatarUrl) {}
}
