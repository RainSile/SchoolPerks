package com.campus.perks.user.mapper;

import com.campus.perks.user.entity.UserAccount;
import com.campus.perks.user.vo.SchoolView;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
  @Select("SELECT code FROM campus_permission ORDER BY code")
  List<String> allPermissionCodes();

  String COLUMNS =
      "SELECT u.id,u.phone,u.nickname,u.school_id,s.name AS"
          + " school_name,u.status,u.account_type,u.access_version FROM campus_user u LEFT JOIN"
          + " campus_school s ON s.id=u.school_id ";

  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  @Select(COLUMNS + "WHERE u.id=#{id}")
  UserAccount find(long id);

  @Select(
      "SELECT id,phone,nickname,school_id,NULL AS school_name,status,account_type,access_version"
          + " FROM campus_user WHERE id=#{id} FOR UPDATE")
  UserAccount lock(long id);

  @Select(
      "SELECT id,phone,nickname,school_id,NULL AS school_name,status,account_type,access_version"
          + " FROM campus_user WHERE phone=#{phone} FOR UPDATE")
  UserAccount lockByPhone(String phone);

  @Insert(
      "INSERT INTO campus_user(phone,nickname) VALUES(#{phone},#{nickname}) ON DUPLICATE KEY UPDATE"
          + " id=id")
  int register(@Param("phone") String phone, @Param("nickname") String nickname);

  @Update("UPDATE campus_user SET nickname=#{nickname} WHERE id=#{id} AND status='ACTIVE'")
  int updateNickname(@Param("id") long id, @Param("nickname") String nickname);

  @Update(
      "UPDATE campus_user SET account_type='MERCHANT',access_version=access_version+1 WHERE"
          + " id=#{id} AND account_type='USER'")
  int promoteMerchant(long id);

  @Update(
      "UPDATE campus_auth_session SET revoked_at=UTC_TIMESTAMP(3) WHERE user_id=#{id} AND"
          + " revoked_at IS NULL")
  int revokeSessions(long id);

  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  @Select(
      "SELECT DISTINCT p.code FROM campus_permission p JOIN campus_role_permission rp ON"
          + " rp.permission_id=p.id JOIN campus_user_role ur ON ur.role_id=rp.role_id JOIN"
          + " campus_role r ON r.id=ur.role_id JOIN campus_user u ON u.id=ur.user_id WHERE"
          + " ur.user_id=#{id} AND r.account_type=u.account_type ORDER BY p.code")
  List<String> permissions(long id);

  @Select(
      "SELECT COUNT(*) FROM campus_auth_session s JOIN campus_user u ON u.id=s.user_id WHERE"
          + " s.id=#{sid} AND u.id=#{user} AND s.access_version=u.access_version")
  int currentAccessVersion(@Param("user") long user, @Param("sid") String sid);

  @Select("SELECT CAST(id AS CHAR) AS id,name FROM campus_school ORDER BY id LIMIT 1000")
  List<SchoolView> schools();

  @Select(
      "SELECT COUNT(*) FROM campus_campus_verification WHERE user_id=#{user} AND"
          + " school_id=#{school} AND status='APPROVED'")
  int verifiedSchool(@Param("user") long user, @Param("school") long school);

  @Select("SELECT COUNT(*) FROM campus_school WHERE id=#{id}")
  int schoolExists(long id);
}
