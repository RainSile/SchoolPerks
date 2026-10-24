package com.campus.perks.admin.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditMapper {
  String SELECT =
      "SELECT id,CAST(actor_id AS CHAR) AS"
          + " actor_id,action,resource_type,resource_id,details,created_at FROM campus_audit_event"
          + " ";
  String FILTER =
      "WHERE (#{type} IS NULL OR resource_type=#{type}) AND (#{resource} IS NULL OR"
          + " resource_id=#{resource}) ";

  @Select(SELECT + FILTER + "ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
  java.util.List<com.campus.perks.admin.entity.AuditEventRow> list(
      @Param("type") String type,
      @Param("resource") String resource,
      @Param("size") int size,
      @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_audit_event " + FILTER)
  long count(@Param("type") String type, @Param("resource") String resource);

  @Select(SELECT + "WHERE id=#{id}")
  com.campus.perks.admin.entity.AuditEventRow find(String id);

  @Insert(
      "INSERT INTO"
          + " campus_audit_event(id,actor_id,action,resource_type,resource_id,details,created_at)"
          + " VALUES(#{id},#{actor},#{action},#{type},#{resource},#{details},#{now})")
  int insert(
      @Param("id") String id,
      @Param("actor") long actor,
      @Param("action") String action,
      @Param("type") String type,
      @Param("resource") String resource,
      @Param("details") String details,
      @Param("now") Instant now);
}
