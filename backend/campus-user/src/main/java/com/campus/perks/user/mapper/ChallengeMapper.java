package com.campus.perks.user.mapper;

import com.campus.perks.user.entity.*;
import java.time.Instant;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ChallengeMapper {
  @Update(
      "UPDATE campus_verification_challenge SET status='SUPERSEDED' WHERE phone=#{phone} AND"
          + " purpose=#{purpose} AND status='SENT'")
  int invalidatePrevious(@Param("phone") String phone, @Param("purpose") String purpose);

  @Insert(
      "INSERT IGNORE INTO campus_verification_rate(rate_key,request_count,window_start)"
          + " VALUES(#{key},0,#{now})")
  int ensureRate(@Param("key") String key, @Param("now") Instant now);

  @Select(
      "SELECT rate_key,request_count,window_start FROM campus_verification_rate WHERE"
          + " rate_key=#{key} FOR UPDATE")
  RateEntity lockRate(String key);

  @Update(
      "UPDATE campus_verification_rate SET request_count=#{count},window_start=#{start} WHERE"
          + " rate_key=#{key}")
  int setRate(@Param("key") String key, @Param("count") int count, @Param("start") Instant start);

  @Insert(
      "INSERT INTO"
          + " campus_verification_challenge(id,phone,purpose,code_hash,attempts,status,created_at,expires_at,verifier_type,provider_reference,provider_scope)"
          + " VALUES(#{id},#{phone},#{purpose},#{codeHash},#{attempts},#{status},#{createdAt},#{expiresAt},#{verifierType},#{providerReference},#{providerScope})")
  int insert(ChallengeEntity entity);

  @Select(
      "SELECT"
          + " id,phone,purpose,code_hash,attempts,status,created_at,expires_at,verifier_type,provider_reference,provider_scope"
          + " FROM campus_verification_challenge WHERE id=#{id} FOR UPDATE")
  ChallengeEntity lock(String id);

  @Select(
      "SELECT"
          + " id,phone,purpose,code_hash,attempts,status,created_at,expires_at,verifier_type,provider_reference,provider_scope"
          + " FROM campus_verification_challenge WHERE id=#{id}")
  ChallengeEntity find(String id);

  @Update(
      "UPDATE campus_verification_challenge SET"
          + " provider_reference=#{reference},provider_scope=#{scope} WHERE id=#{id}")
  int delivery(
      @Param("id") String id, @Param("reference") String reference, @Param("scope") String scope);

  @Update("UPDATE campus_verification_challenge SET status=#{status} WHERE id=#{id}")
  int status(@Param("id") String id, @Param("status") String status);

  @Update("UPDATE campus_verification_challenge SET attempts=attempts+1 WHERE id=#{id}")
  int failedAttempt(String id);
}
