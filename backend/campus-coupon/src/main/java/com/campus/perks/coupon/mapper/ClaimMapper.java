package com.campus.perks.coupon.mapper;

import com.campus.perks.coupon.entity.*;
import com.campus.perks.coupon.vo.RedemptionView;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClaimMapper {
  String COLUMNS =
      "SELECT"
          + " id,coupon_id,user_id,redemption_code_hash,status,claimed_at,redeemed_at,code_expires_at,code_issued_at"
          + " FROM campus_coupon_claim ";

  @Select(COLUMNS + "WHERE id=#{id}")
  ClaimRow find(long id);

  @Select(COLUMNS + "WHERE id=#{id} FOR UPDATE")
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  ClaimRow lock(long id);

  @Select(COLUMNS + "WHERE coupon_id=#{coupon} AND user_id=#{user} FOR UPDATE")
  ClaimRow existing(@Param("coupon") long coupon, @Param("user") long user);

  @Insert(
      "INSERT INTO campus_coupon_claim(coupon_id,user_id,redemption_code_hash,status,claimed_at)"
          + " VALUES(#{coupon},#{user},#{hash},'AVAILABLE',#{now})")
  void insert(
      @Param("coupon") long coupon,
      @Param("user") long user,
      @Param("hash") String hash,
      @Param("now") Instant now);

  @Select("SELECT id FROM campus_coupon_claim WHERE coupon_id=#{coupon} AND user_id=#{user}")
  long id(@Param("coupon") long coupon, @Param("user") long user);

  @Select(COLUMNS + "WHERE user_id=#{user} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
  List<ClaimRow> list(
      @Param("user") long user, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_coupon_claim WHERE user_id=#{user}")
  long count(long user);

  @Update(
      "UPDATE campus_coupon_claim SET"
          + " redemption_code_hash=#{hash},code_expires_at=#{expires},code_issued_at=#{now} WHERE"
          + " id=#{id} AND status='AVAILABLE'")
  int ticket(
      @Param("id") long id,
      @Param("hash") String hash,
      @Param("expires") Instant expires,
      @Param("now") Instant now);

  @Update(
      "UPDATE campus_coupon_claim SET status='REDEEMED',redeemed_at=#{now} WHERE id=#{id} AND"
          + " status='AVAILABLE'")
  int redeem(@Param("id") long id, @Param("now") Instant now);

  @Insert(
      "INSERT INTO campus_redemption(claim_id,merchant_id,operator_id,redeemed_at)"
          + " VALUES(#{claim},#{merchant},#{operator},#{now})")
  void redemption(
      @Param("claim") long claim,
      @Param("merchant") long merchant,
      @Param("operator") long operator,
      @Param("now") Instant now);

  @Select(
      "SELECT CAST(id AS CHAR) AS id,CAST(claim_id AS CHAR) AS claim_id,CAST(merchant_id AS CHAR)"
          + " AS merchant_id,CAST(operator_id AS CHAR) AS operator_id,redeemed_at FROM"
          + " campus_redemption WHERE claim_id=#{claim}")
  RedemptionView redemptionByClaim(long claim);

  @Select(
      "SELECT CAST(id AS CHAR) AS id,CAST(claim_id AS CHAR) AS claim_id,CAST(merchant_id AS CHAR)"
          + " AS merchant_id,CAST(operator_id AS CHAR) AS operator_id,redeemed_at FROM"
          + " campus_redemption WHERE id=#{id}")
  RedemptionView redemptionById(long id);

  @Select(
      "SELECT CAST(id AS CHAR) AS id,CAST(claim_id AS CHAR) AS claim_id,CAST(merchant_id AS CHAR)"
          + " AS merchant_id,CAST(operator_id AS CHAR) AS operator_id,redeemed_at FROM"
          + " campus_redemption WHERE merchant_id=#{merchant} ORDER BY id DESC LIMIT #{size} OFFSET"
          + " #{offset}")
  List<RedemptionView> redemptionList(
      @Param("merchant") long merchant, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_redemption WHERE merchant_id=#{merchant}")
  long redemptionCount(long merchant);

  @Select(
      "SELECT window_start,attempts FROM campus_redemption_attempt_rate WHERE operator_id=#{actor}"
          + " FOR UPDATE")
  RedemptionRate rate(long actor);

  @Insert(
      "INSERT INTO campus_redemption_attempt_rate(operator_id,window_start,attempts)"
          + " VALUES(#{actor},#{now},1) ON DUPLICATE KEY UPDATE"
          + " window_start=VALUES(window_start),attempts=1")
  void resetRate(@Param("actor") long actor, @Param("now") Instant now);

  @Update(
      "UPDATE campus_redemption_attempt_rate SET attempts=attempts+1 WHERE operator_id=#{actor}")
  void incrementRate(long actor);
}
