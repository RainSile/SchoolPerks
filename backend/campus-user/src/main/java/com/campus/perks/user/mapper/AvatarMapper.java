package com.campus.perks.user.mapper;
import com.campus.perks.user.entity.AvatarEntity;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AvatarMapper {
  @Select("SELECT user_id,file_id,default_key,updated_at FROM campus_user_avatar WHERE user_id=#{id}")
  AvatarEntity find(long id);
  @Insert("INSERT INTO campus_user_avatar(user_id,file_id,default_key,updated_at) VALUES(#{userId},#{fileId},#{defaultKey},#{updatedAt}) ON DUPLICATE KEY UPDATE file_id=VALUES(file_id),default_key=VALUES(default_key),updated_at=VALUES(updated_at)")
  int save(AvatarEntity value);
}
