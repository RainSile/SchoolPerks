package com.campus.perks.user.mapper;

import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.CampusReviewPort.Entry;
import com.campus.perks.user.entity.CampusVerificationEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CampusReviewMapper {
  @Select(
      "SELECT v.id,CAST(v.user_id AS CHAR) AS applicant_id,CAST(v.school_id AS CHAR) AS"
          + " school_id,s.name AS school_name,v.status,v.submitted_at FROM"
          + " campus_campus_verification v JOIN campus_school s ON s.id=v.school_id WHERE"
          + " v.status=#{status} ORDER BY v.submitted_at,v.id LIMIT #{size} OFFSET #{offset}")
  List<Entry> queue(
      @Param("status") String status, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_campus_verification WHERE status=#{status}")
  long count(String status);

  @Select(
      "SELECT v.id,v.user_id,v.school_id,s.name AS"
          + " school_name,v.status,v.submitted_at,v.reviewed_at,v.review_reason FROM"
          + " campus_campus_verification v JOIN campus_school s ON s.id=v.school_id WHERE"
          + " v.id=#{id}")
  CampusVerificationEntity find(String id);

  @Select(
      "SELECT v.id,v.user_id,v.school_id,s.name AS"
          + " school_name,v.status,v.submitted_at,v.reviewed_at,v.review_reason FROM"
          + " campus_campus_verification v JOIN campus_school s ON s.id=v.school_id WHERE"
          + " v.id=#{id} FOR UPDATE")
  CampusVerificationEntity lock(String id);

  @Select("SELECT encrypted_identity FROM campus_campus_verification WHERE id=#{id}")
  String identity(String id);

  @Update(
      "UPDATE campus_campus_verification SET"
          + " status=#{decision},reviewed_at=#{now},review_reason=#{reason} WHERE id=#{id} AND"
          + " status='PENDING'")
  int decide(
      @Param("id") String id,
      @Param("decision") String decision,
      @Param("reason") String reason,
      @Param("now") Instant now);

  @Update("UPDATE campus_user SET school_id=#{school} WHERE id=#{user} AND status='ACTIVE'")
  int approveSchool(@Param("user") long user, @Param("school") long school);

  @Insert(
      "INSERT INTO"
          + " campus_campus_verification_review(id,verification_id,reviewer_id,decision,reason,created_at)"
          + " VALUES(#{id},#{resourceId},#{reviewerId},#{decision},#{reason},#{createdAt})")
  int insertReview(ReviewRecord record);

  @Select(
      "SELECT id,verification_id AS resource_id,CAST(reviewer_id AS CHAR) AS"
          + " reviewer_id,decision,reason,created_at FROM campus_campus_verification_review WHERE"
          + " id=#{id}")
  ReviewRecord review(String id);
}
