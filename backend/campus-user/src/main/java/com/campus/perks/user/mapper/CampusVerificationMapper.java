package com.campus.perks.user.mapper;

import com.campus.perks.user.entity.CampusVerificationEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CampusVerificationMapper {
  String SELECT =
      "SELECT v.id,v.user_id,v.school_id,s.name AS"
          + " school_name,v.status,v.submitted_at,v.reviewed_at,v.review_reason FROM"
          + " campus_campus_verification v JOIN campus_school s ON s.id=v.school_id ";

  @Select(SELECT + "WHERE v.user_id=#{userId} ORDER BY v.submitted_at DESC,v.id DESC LIMIT 100")
  List<CampusVerificationEntity> list(long userId);

  @Select(SELECT + "WHERE v.user_id=#{userId} AND v.id=#{id}")
  CampusVerificationEntity own(@Param("userId") long userId, @Param("id") String id);

  @Select(
      "SELECT COUNT(*) FROM campus_campus_verification WHERE user_id=#{userId} AND status IN"
          + " ('PENDING','APPROVED')")
  int active(long userId);

  @Insert(
      "INSERT INTO"
          + " campus_campus_verification(id,user_id,school_id,encrypted_identity,status,submitted_at)"
          + " VALUES(#{id},#{userId},#{schoolId},#{identity},'PENDING',#{now})")
  int insert(
      @Param("id") String id,
      @Param("userId") long userId,
      @Param("schoolId") long schoolId,
      @Param("identity") String identity,
      @Param("now") Instant now);
}
