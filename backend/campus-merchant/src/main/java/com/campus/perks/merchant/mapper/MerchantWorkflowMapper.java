package com.campus.perks.merchant.mapper;

import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.merchant.entity.MerchantApplicationRow;
import com.campus.perks.merchant.vo.MemberView;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MerchantWorkflowMapper {
  @Select(
      "SELECT CAST(mm.user_id AS CHAR) AS user_id,u.nickname,mm.member_role AS role,u.status AS"
          + " user_status,mm.created_at FROM campus_merchant_member mm JOIN campus_user u ON"
          + " u.id=mm.user_id WHERE mm.merchant_id=#{merchant} AND mm.user_id=#{user}")
  MemberView member(@Param("merchant") long merchant, @Param("user") long user);

  @Select(
      "SELECT id,CAST(merchant_id AS CHAR) AS"
          + " merchant_id,previous_status,status,version,CAST(actor_id AS CHAR) AS"
          + " actor_id,reason,created_at FROM campus_merchant_status_change WHERE id=#{id} AND"
          + " merchant_id=#{merchant}")
  com.campus.perks.integration.port.MerchantReviewPort.StatusChange statusChange(
      @Param("id") String id, @Param("merchant") long merchant);

  String APPLICATION =
      "SELECT a.id,a.applicant_id,a.school_id,s.name AS"
          + " school_name,a.name,a.category,a.address,a.longitude,a.latitude,a.description,a.qualification_file_id,a.status,a.merchant_id,a.review_reason,a.submitted_at,a.reviewed_at"
          + " FROM campus_merchant_application a JOIN campus_school s ON s.id=a.school_id ";
  String REVIEW =
      "SELECT id,application_id AS resource_id,CAST(reviewer_id AS CHAR) AS"
          + " reviewer_id,decision,reason,created_at FROM campus_merchant_review ";

  @Select(APPLICATION + "WHERE a.id=#{id}")
  MerchantApplicationRow find(String id);

  @Select(APPLICATION + "WHERE a.id=#{id} FOR UPDATE")
  MerchantApplicationRow lock(String id);

  @Select(
      APPLICATION
          + "WHERE a.applicant_id=#{owner} ORDER BY a.submitted_at DESC,a.id DESC LIMIT #{size}"
          + " OFFSET #{offset}")
  List<MerchantApplicationRow> ownList(
      @Param("owner") long owner, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_merchant_application WHERE applicant_id=#{owner}")
  long ownCount(long owner);

  @Select(
      "SELECT COUNT(*) FROM campus_merchant_application WHERE applicant_id=#{owner} AND"
          + " status='PENDING'")
  int pending(long owner);

  @Insert(
      "INSERT INTO"
          + " campus_merchant_application(id,applicant_id,school_id,name,category,address,longitude,latitude,description,qualification_file_id,status,submitted_at)"
          + " VALUES(#{id},#{applicantId},#{schoolId},#{name},#{category},#{address},#{longitude},#{latitude},#{description},#{qualificationFileId},'PENDING',#{submittedAt})")
  int insert(MerchantApplicationRow row);

  @Select(
      APPLICATION
          + "WHERE a.status=#{status} ORDER BY a.submitted_at,a.id LIMIT #{size} OFFSET #{offset}")
  List<MerchantApplicationRow> queue(
      @Param("status") String status, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_merchant_application WHERE status=#{status}")
  long queueCount(String status);

  @Update(
      "UPDATE campus_merchant_application SET"
          + " status=#{status},merchant_id=#{merchant},review_reason=#{reason},reviewed_at=#{now}"
          + " WHERE id=#{id} AND status='PENDING'")
  int decide(
      @Param("id") String id,
      @Param("status") String status,
      @Param("merchant") Long merchant,
      @Param("reason") String reason,
      @Param("now") Instant now);

  @Insert(
      "INSERT INTO campus_merchant_review(id,application_id,reviewer_id,decision,reason,created_at)"
          + " VALUES(#{id},#{resourceId},#{reviewerId},#{decision},#{reason},#{createdAt})")
  int review(ReviewRecord record);

  @Select(REVIEW + "ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
  List<ReviewRecord> history(@Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_merchant_review")
  long historyCount();

  @Select(REVIEW + "WHERE id=#{id}")
  ReviewRecord reviewById(String id);

  @Select(
      "SELECT member_role FROM campus_merchant_member WHERE merchant_id=#{merchant} AND"
          + " user_id=#{user} FOR SHARE")
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  String role(@Param("merchant") long merchant, @Param("user") long user);

  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  @Select(
      "SELECT COUNT(*) FROM campus_merchant_member mm JOIN campus_merchant m ON m.id=mm.merchant_id"
          + " WHERE mm.user_id=#{user} AND m.status IN ('APPROVED','SUSPENDED')")
  int memberships(long user);

  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  @Select(
      "SELECT COUNT(*) FROM campus_merchant_member mm JOIN campus_merchant m ON m.id=mm.merchant_id"
          + " WHERE mm.user_id=#{user} AND mm.member_role IN ('OWNER','MANAGER') AND m.status IN"
          + " ('APPROVED','SUSPENDED')")
  int dashboardMemberships(long user);

  @Select(
      "SELECT merchant_id FROM campus_merchant_member WHERE user_id=#{user} ORDER BY merchant_id"
          + " LIMIT #{size} OFFSET #{offset}")
  List<Long> ownMerchantIds(
      @Param("user") long user, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_merchant_member WHERE user_id=#{user}")
  long ownMerchantCount(long user);

  @Select("SELECT id FROM campus_merchant WHERE id=#{id} FOR UPDATE")
  Long lockMerchant(long id);

  @Select("SELECT COUNT(*) FROM campus_merchant_member WHERE merchant_id=#{id}")
  int memberCount(long id);

  @Select(
      "SELECT CAST(mm.user_id AS CHAR) AS user_id,u.nickname,mm.member_role AS role,u.status AS"
          + " user_status,mm.created_at FROM campus_merchant_member mm JOIN campus_user u ON"
          + " u.id=mm.user_id WHERE mm.merchant_id=#{merchant} ORDER BY mm.created_at,mm.user_id"
          + " LIMIT #{size} OFFSET #{offset}")
  List<MemberView> members(
      @Param("merchant") long merchant, @Param("size") int size, @Param("offset") long offset);

  @Insert(
      "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role,created_at)"
          + " VALUES(#{merchant},#{user},#{role},#{now})")
  int addMember(
      @Param("merchant") long merchant,
      @Param("user") long user,
      @Param("role") String role,
      @Param("now") Instant now);

  @Delete(
      "DELETE FROM campus_merchant_member WHERE merchant_id=#{merchant} AND user_id=#{user} AND"
          + " member_role<>'OWNER'")
  int removeMember(@Param("merchant") long merchant, @Param("user") long user);

  @Update(
      "UPDATE campus_merchant SET"
          + " description=#{description},business_hours=#{hours},version=version+1 WHERE id=#{id}"
          + " AND version=#{version} AND status='APPROVED'")
  int update(
      @Param("id") long id,
      @Param("version") int version,
      @Param("description") String description,
      @Param("hours") String hours);

  @Update(
      "UPDATE campus_merchant SET status=#{status},version=version+1 WHERE id=#{id} AND"
          + " version=#{version}")
  int status(@Param("id") long id, @Param("version") int version, @Param("status") String status);

  @Insert(
      "INSERT INTO"
          + " campus_merchant_status_change(id,merchant_id,actor_id,previous_status,status,reason,version,created_at)"
          + " VALUES(#{id},#{merchant},#{actor},#{previous},#{status},#{reason},#{version},#{now})")
  int statusHistory(
      @Param("id") String id,
      @Param("merchant") long merchant,
      @Param("actor") long actor,
      @Param("previous") String previous,
      @Param("status") String status,
      @Param("reason") String reason,
      @Param("version") int version,
      @Param("now") Instant now);
}
