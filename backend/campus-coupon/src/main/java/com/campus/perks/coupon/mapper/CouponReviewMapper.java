package com.campus.perks.coupon.mapper;

import com.campus.perks.coupon.entity.ReviewRequestRow;
import com.campus.perks.integration.contract.ReviewRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponReviewMapper {
  String COLUMNS =
      "SELECT"
          + " id,coupon_id,submitted_by,content_version,content_snapshot,status,created_at,reviewed_at"
          + " FROM campus_coupon_review_request ";

  @Insert(
      "INSERT INTO"
          + " campus_coupon_review_request(id,coupon_id,submitted_by,content_version,content_snapshot,status,created_at)"
          + " VALUES(#{id},#{couponId},#{submittedBy},#{contentVersion},#{contentSnapshot},'PENDING',#{createdAt})")
  void request(ReviewRequestRow row);

  @Select(COLUMNS + "WHERE id=#{id}")
  ReviewRequestRow find(String id);

  @Select(COLUMNS + "WHERE id=#{id} FOR UPDATE")
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  ReviewRequestRow lock(String id);

  @Select(COLUMNS + "WHERE status=#{status} ORDER BY created_at,id LIMIT #{size} OFFSET #{offset}")
  List<ReviewRequestRow> queue(
      @Param("status") String status, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_coupon_review_request WHERE status=#{status}")
  long count(String status);

  @Update(
      "UPDATE campus_coupon_review_request SET status=#{decision},reviewed_at=#{now} WHERE id=#{id}"
          + " AND status='PENDING'")
  int decide(@Param("id") String id, @Param("decision") String decision, @Param("now") Instant now);

  @Insert(
      "INSERT INTO campus_coupon_review(id,request_id,reviewer_id,decision,reason,created_at)"
          + " VALUES(#{id},#{resourceId},#{reviewerId},#{decision},#{reason},#{createdAt})")
  void insertReview(ReviewRecord record);

  @Select(
      "SELECT id,request_id AS resource_id,CAST(reviewer_id AS CHAR) AS"
          + " reviewer_id,decision,reason,created_at FROM campus_coupon_review WHERE id=#{id}")
  ReviewRecord selectReview(String id);

  @Select(
      "SELECT id,request_id AS resource_id,CAST(reviewer_id AS CHAR) AS"
          + " reviewer_id,decision,reason,created_at FROM campus_coupon_review WHERE"
          + " request_id=#{id}")
  ReviewRecord selectReviewByRequest(String id);
}
