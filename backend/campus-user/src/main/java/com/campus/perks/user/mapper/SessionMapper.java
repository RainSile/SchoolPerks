package com.campus.perks.user.mapper;

import com.campus.perks.user.entity.*;
import java.time.Instant;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SessionMapper {
  @Select(
      "SELECT token_hash,session_id,expires_at,consumed_at FROM campus_refresh_token WHERE"
          + " token_hash=#{hash} FOR UPDATE")
  RefreshTokenEntity lockToken(String hash);

  @Insert(
      "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version)"
          + " VALUES(#{id},#{userId},#{createdAt},#{expiresAt},(SELECT access_version FROM"
          + " campus_user WHERE id=#{userId}))")
  int insert(SessionEntity entity);

  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  @Select(
      "SELECT id,user_id,created_at,expires_at,revoked_at FROM campus_auth_session WHERE id=#{id}")
  SessionEntity find(String id);

  @Select(
      "SELECT id,user_id,created_at,expires_at,revoked_at FROM campus_auth_session WHERE id=#{id}"
          + " FOR UPDATE")
  SessionEntity lock(String id);

  @Update("UPDATE campus_auth_session SET revoked_at=#{now} WHERE id=#{id} AND revoked_at IS NULL")
  int revoke(@Param("id") String id, @Param("now") Instant now);

  @Insert(
      "INSERT INTO campus_refresh_token(token_hash,session_id,expires_at)"
          + " VALUES(#{hash},#{sessionId},#{expires})")
  int insertToken(
      @Param("hash") String hash,
      @Param("sessionId") String sessionId,
      @Param("expires") Instant expires);

  @Select(
      "SELECT token_hash,session_id,expires_at,consumed_at FROM campus_refresh_token WHERE"
          + " token_hash=#{hash}")
  RefreshTokenEntity token(String hash);

  @Update(
      "UPDATE campus_refresh_token SET consumed_at=#{now} WHERE token_hash=#{hash} AND consumed_at"
          + " IS NULL")
  int consume(@Param("hash") String hash, @Param("now") Instant now);
}
