package com.campus.perks.file.mapper;

import com.campus.perks.file.entity.FileObject;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FileMapper {
  String SELECT =
      "SELECT"
          + " id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at,completed_at"
          + " FROM campus_file_object ";

  @Select(SELECT + "WHERE id=#{id}")
  FileObject find(String id);

  @Select(SELECT + "WHERE id=#{id} FOR UPDATE")
  FileObject lock(String id);

  @Select(
      SELECT
          + "WHERE owner_id=#{owner} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET"
          + " #{offset}")
  List<FileObject> list(
      @Param("owner") long owner, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_file_object WHERE owner_id=#{owner}")
  long count(long owner);

  @Select(
      "SELECT COUNT(*) FROM campus_file_object WHERE owner_id=#{owner} AND status='PENDING' AND"
          + " expires_at>#{now}")
  int pending(@Param("owner") long owner, @Param("now") Instant now);

  @Insert(
      "INSERT INTO"
          + " campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at)"
          + " VALUES(#{id},#{ownerId},#{purpose},#{originalName},#{contentType},#{declaredSize},#{stagingKey},#{immutableKey},#{status},#{createdAt},#{expiresAt})")
  int insert(FileObject row);

  @Update(
      "UPDATE campus_file_object SET status='READY',completed_at=#{now} WHERE id=#{id} AND"
          + " status='PENDING'")
  int complete(@Param("id") String id, @Param("now") Instant now);
}
