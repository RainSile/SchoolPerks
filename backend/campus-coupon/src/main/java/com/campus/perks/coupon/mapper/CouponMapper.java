package com.campus.perks.coupon.mapper;

import com.campus.perks.coupon.entity.*;
import com.campus.perks.coupon.vo.CouponStatusChange;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponMapper {
  String COLUMNS =
      "SELECT"
          + " id,merchant_id,title,coupon_type,rules,total_stock,remaining_stock,valid_from,valid_until,status,version,description,campus_only,review_status,review_request_id,published_at"
          + " FROM campus_coupon ";

  @Select(COLUMNS + "WHERE id=#{id}")
  CouponRow find(long id);

  @Select(COLUMNS + "WHERE id=#{id} FOR UPDATE")
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  CouponRow lock(long id);

  @Insert(
      "INSERT INTO"
          + " campus_coupon(merchant_id,title,coupon_type,rules,total_stock,remaining_stock,valid_from,valid_until,description,campus_only)"
          + " VALUES(#{merchantId},#{title},#{couponType},#{rules},#{totalStock},#{remainingStock},#{validFrom},#{validUntil},#{description},#{campusOnly})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(CouponEntity entity);

  @Update(
      "UPDATE campus_coupon SET"
          + " title=#{c.title},coupon_type=#{c.couponType},rules=#{c.rules},total_stock=#{c.totalStock},remaining_stock=#{c.remainingStock},valid_from=#{c.validFrom},valid_until=#{c.validUntil},description=#{c.description},campus_only=#{c.campusOnly},review_status='NONE',review_request_id=NULL,version=version+1"
          + " WHERE id=#{id} AND version=#{version}")
  int update(@Param("id") long id, @Param("version") int version, @Param("c") CouponEntity entity);

  @Select(COLUMNS + "WHERE merchant_id=#{merchant} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
  List<CouponRow> merchantList(
      @Param("merchant") long merchant, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_coupon WHERE merchant_id=#{merchant}")
  long merchantCount(long merchant);

  @Update(
      "UPDATE campus_coupon SET"
          + " review_status='PENDING',review_request_id=#{request},version=version+1 WHERE id=#{id}"
          + " AND version=#{version}")
  int requestReview(
      @Param("id") long id, @Param("version") int version, @Param("request") String request);

  @Update(
      "UPDATE campus_coupon SET review_status=#{decision},version=version+1 WHERE id=#{id} AND"
          + " review_request_id=#{request} AND review_status='PENDING'")
  int review(
      @Param("id") long id, @Param("request") String request, @Param("decision") String decision);

  @Update(
      "UPDATE campus_coupon SET status=#{status},version=version+1,published_at=CASE WHEN"
          + " #{status}='PUBLISHED' THEN #{now} ELSE published_at END WHERE id=#{id} AND"
          + " version=#{version}")
  int status(
      @Param("id") long id,
      @Param("version") int version,
      @Param("status") String status,
      @Param("now") Instant now);

  @Update(
      "UPDATE campus_coupon SET remaining_stock=remaining_stock-1 WHERE id=#{id} AND"
          + " remaining_stock>0 AND status='PUBLISHED'")
  int decrement(long id);

  @Insert(
      "INSERT INTO"
          + " campus_coupon_status_change(id,coupon_id,actor_id,previous_status,status,version,reason,created_at)"
          + " VALUES(#{id},#{couponId},#{actorId},#{previousStatus},#{status},#{version},#{reason},#{createdAt})")
  void statusHistory(CouponStatusChange record);

  @Select(
      "SELECT id,CAST(coupon_id AS CHAR) AS coupon_id,CAST(actor_id AS CHAR) AS"
          + " actor_id,previous_status,status,version,reason,created_at FROM"
          + " campus_coupon_status_change WHERE id=#{id} AND coupon_id=#{coupon}")
  CouponStatusChange statusChange(@Param("coupon") long coupon, @Param("id") String id);
}
