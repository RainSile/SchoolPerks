package com.campus.perks.user.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface PasswordMapper {
  @Select("SELECT password_hash FROM campus_user WHERE id=#{id}")
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  String hash(long id);

  @Insert(
      "INSERT INTO campus_user(phone,nickname,password_hash,account_type)"
          + " VALUES(#{phone},#{nickname},#{hash},'USER')")
  int register(
      @Param("phone") String phone, @Param("nickname") String nickname, @Param("hash") String hash);

  @Update(
      "UPDATE campus_user SET password_hash=#{hash},access_version=access_version+1 WHERE id=#{id}")
  int update(@Param("id") long id, @Param("hash") String hash);
}
