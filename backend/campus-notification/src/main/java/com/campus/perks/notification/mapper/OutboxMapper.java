package com.campus.perks.notification.mapper;

import com.campus.perks.notification.dto.DeliveryQuery;
import com.campus.perks.notification.entity.OutboxRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OutboxMapper {
  String COLUMNS =
      "SELECT"
          + " id,aggregate_id,event_type,payload,status,attempts,next_attempt_at,created_at,published_at,lease_token,locked_until,last_error_code"
          + " FROM campus_outbox_event ";

  @Insert(
      "INSERT INTO"
          + " campus_outbox_event(id,aggregate_id,event_type,payload,status,attempts,next_attempt_at,created_at)"
          + " VALUES(#{id},#{aggregate},#{type},#{payload},'PENDING',0,#{now},#{now})")
  void insert(
      @Param("id") String id,
      @Param("aggregate") String aggregate,
      @Param("type") String type,
      @Param("payload") String payload,
      @Param("now") Instant now);

  @Update(
      "UPDATE campus_outbox_event SET"
          + " status='FAILED',last_error_code='LEASE_EXHAUSTED',lease_token=NULL,locked_until=NULL"
          + " WHERE status='PROCESSING' AND locked_until<=#{now} AND attempts>=#{max}")
  void exhausted(@Param("now") Instant now, @Param("max") int max);

  @Select(
      COLUMNS
          + "WHERE ((status='PENDING' AND next_attempt_at<=#{now}) OR (status='PROCESSING' AND"
          + " locked_until<=#{now})) AND attempts<#{max} ORDER BY next_attempt_at,id LIMIT 1 FOR"
          + " UPDATE SKIP LOCKED")
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  OutboxRow next(@Param("now") Instant now, @Param("max") int max);

  @Update(
      "UPDATE campus_outbox_event SET"
          + " status='PROCESSING',attempts=attempts+1,lease_token=#{token},locked_until=#{until}"
          + " WHERE id=#{id}")
  void lease(@Param("id") String id, @Param("token") String token, @Param("until") Instant until);

  @Select(COLUMNS + "WHERE id=#{id}")
  OutboxRow find(String id);

  @Select(COLUMNS + "WHERE id=#{id} FOR UPDATE")
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  OutboxRow lock(String id);

  @Update(
      "UPDATE campus_outbox_event SET"
          + " status='PUBLISHED',published_at=#{now},lease_token=NULL,locked_until=NULL,last_error_code=NULL"
          + " WHERE id=#{id} AND status='PROCESSING' AND lease_token=#{token}")
  int success(@Param("id") String id, @Param("token") String token, @Param("now") Instant now);

  @Update(
      "UPDATE campus_outbox_event SET"
          + " status=#{status},next_attempt_at=#{next},lease_token=NULL,locked_until=NULL,last_error_code='DELIVERY_FAILED'"
          + " WHERE id=#{id} AND status='PROCESSING' AND lease_token=#{token}")
  int failure(
      @Param("id") String id,
      @Param("token") String token,
      @Param("status") String status,
      @Param("next") Instant next);

  @Update(
      "UPDATE campus_outbox_event SET"
          + " status='PENDING',attempts=0,next_attempt_at=#{now},lease_token=NULL,locked_until=NULL,last_error_code=NULL"
          + " WHERE id=#{id} AND status='FAILED'")
  int retry(@Param("id") String id, @Param("now") Instant now);

  @Select(
      COLUMNS
          + "WHERE status=#{q.status} ORDER BY created_at DESC,id DESC LIMIT #{q.size} OFFSET"
          + " #{offset}")
  List<OutboxRow> list(@Param("q") DeliveryQuery q, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM campus_outbox_event WHERE status=#{status}")
  long count(String status);
}
