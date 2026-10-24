package com.campus.perks.admin.mapper;

import com.campus.perks.admin.dto.OperationAuditQuery;
import com.campus.perks.security.port.OperationAuditPort.Event;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class OperationAuditMapper {
  private final JdbcTemplate db;

  public OperationAuditMapper(JdbcTemplate db) {
    this.db = db;
  }

  public void insert(Event e) {
    db.update(
        "INSERT INTO"
            + " campus_operation_audit(id,actor_id,action,resource_type,resource_id,outcome,http_status,error_code,duration_ms,created_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        e.id(),
        e.actorId(),
        e.action(),
        e.resourceType(),
        e.resourceId(),
        e.outcome(),
        e.httpStatus(),
        e.errorCode(),
        e.durationMillis(),
        Timestamp.from(e.createdAt()));
  }

  private String filter(OperationAuditQuery q, List<Object> args, Instant from, Instant to) {
    String sql = " WHERE created_at>=? AND created_at<?";
    args.add(Timestamp.from(from));
    args.add(Timestamp.from(to));
    if (q.getAction() != null) {
      sql += " AND action=?";
      args.add(q.getAction());
    }
    if (q.getOutcome() != null) {
      sql += " AND outcome=?";
      args.add(q.getOutcome());
    }
    if (q.getActorId() != null) {
      sql += " AND actor_id=?";
      args.add(q.getActorId());
    }
    return sql;
  }

  public List<Event> list(OperationAuditQuery q, Instant from, Instant to) {
    var args = new ArrayList<Object>();
    String sql = filter(q, args, from, to);
    args.add(q.getSize());
    args.add((long) (q.getPage() - 1) * q.getSize());
    return db.query(
        "SELECT * FROM campus_operation_audit"
            + sql
            + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
        (r, n) ->
            new Event(
                r.getString("id"),
                r.getString("actor_id"),
                r.getString("action"),
                r.getString("resource_type"),
                r.getString("resource_id"),
                r.getString("outcome"),
                r.getInt("http_status"),
                r.getString("error_code"),
                r.getLong("duration_ms"),
                r.getTimestamp("created_at").toInstant()),
        args.toArray());
  }

  public long count(OperationAuditQuery q, Instant from, Instant to) {
    var args = new ArrayList<Object>();
    String sql = filter(q, args, from, to);
    return db.queryForObject(
        "SELECT COUNT(*) FROM campus_operation_audit" + sql, Long.class, args.toArray());
  }
}
