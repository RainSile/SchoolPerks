package com.campus.perks.notification.mapper;

import com.campus.perks.notification.entity.NotificationRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NotificationMapper {
  String COLUMNS =
      "SELECT id,event_id,user_id,event_type,payload,created_at,read_at FROM campus_notification ";

  @Insert(
      "INSERT INTO campus_notification(id,event_id,user_id,event_type,payload,created_at)"
          + " VALUES(#{id},#{event},#{user},#{type},#{payload},#{now}) ON DUPLICATE KEY UPDATE"
          + " id=id")
  void insert(
      @Param("id") String id,
      @Param("event") String event,
      @Param("user") long user,
      @Param("type") String type,
      @Param("payload") String payload,
      @Param("now") Instant now);

  @Select(COLUMNS + "WHERE event_id=#{event} AND user_id=#{user}")
  NotificationRow event(@Param("event") String event, @Param("user") long user);

  @Select(COLUMNS + "WHERE id=#{id} AND user_id=#{user}")
  NotificationRow own(@Param("id") String id, @Param("user") long user);

  @Select(
      COLUMNS
          + "WHERE user_id=#{user} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
  List<NotificationRow> list(
      @Param("user") long user, @Param("size") int size, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_notification WHERE user_id=#{user}")
  long count(long user);

  @Update(
      "UPDATE campus_notification SET read_at=COALESCE(read_at,#{now}) WHERE id=#{id} AND"
          + " user_id=#{user}")
  void read(@Param("id") String id, @Param("user") long user, @Param("now") Instant now);
}
