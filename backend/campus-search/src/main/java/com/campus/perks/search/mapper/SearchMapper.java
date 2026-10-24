package com.campus.perks.search.mapper;

import com.campus.perks.integration.port.SearchEnginePort.*;
import com.campus.perks.search.entity.SearchSource;
import com.campus.perks.search.vo.SearchViews.Rebuild;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class SearchMapper {
  private final JdbcTemplate jdbc;

  public SearchMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<SearchSource> SOURCE =
      (r, n) ->
          new SearchSource(
              r.getString("resource_type"),
              r.getString("resource_id"),
              r.getString("school_id"),
              r.getString("merchant_id"),
              r.getString("title"),
              r.getString("body"));
  private static final RowMapper<Rebuild> REBUILD =
      (r, n) ->
          new Rebuild(
              r.getString("id"),
              r.getString("status"),
              r.getLong("processed"),
              r.getString("cursor_type"),
              r.getString("cursor_id"),
              r.getString("error_code"),
              r.getTimestamp("created_at").toInstant(),
              r.getTimestamp("completed_at") == null
                  ? null
                  : r.getTimestamp("completed_at").toInstant());

  public SearchSource source(String type, String id) {
    var values =
        jdbc.query(
            "SELECT * FROM campus_search_source WHERE resource_type=? AND resource_id=?",
            SOURCE,
            type,
            id);
    return values.isEmpty() ? null : values.get(0);
  }

  public com.campus.perks.integration.port.PublicContentPort.Revision revision(
      String type, String id) {
    var rows =
        jdbc.query(
            "SELECT source_version,valid_from,valid_until FROM campus_search_source WHERE"
                + " resource_type=? AND resource_id=?",
            (r, n) ->
                new com.campus.perks.integration.port.PublicContentPort.Revision(
                    r.getString(1),
                    r.getTimestamp(2) == null ? null : r.getTimestamp(2).toInstant(),
                    r.getTimestamp(3) == null ? null : r.getTimestamp(3).toInstant()),
            type,
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public List<SearchSource> sources(Collection<Key> keys) {
    if (keys.isEmpty()) return List.of();
    var args = new ArrayList<Object>();
    var conditions = new ArrayList<String>();
    for (var k : keys) {
      conditions.add("(?,?)");
      args.add(k.type());
      args.add(k.id());
    }
    return jdbc.query(
        "SELECT * FROM campus_search_source WHERE (resource_type,resource_id) IN ("
            + String.join(",", conditions)
            + ")",
        SOURCE,
        args.toArray());
  }

  public long lockDocument(String type, String id, Instant now) {
    jdbc.update(
        "INSERT INTO campus_search_document(resource_type,resource_id,body,updated_at)"
            + " VALUES(?,?,'',?) ON DUPLICATE KEY UPDATE resource_id=resource_id",
        type,
        id,
        Timestamp.from(now));
    return jdbc.queryForObject(
        "SELECT revision FROM campus_search_document WHERE resource_type=? AND resource_id=? FOR"
            + " UPDATE",
        Long.class,
        type,
        id);
  }

  public void update(Document d, Instant now) {
    jdbc.update(
        "UPDATE campus_search_document SET"
            + " school_id=?,merchant_id=?,title=?,body=?,source_hash=?,visible=?,revision=?,updated_at=?"
            + " WHERE resource_type=? AND resource_id=?",
        d.schoolId(),
        d.merchantId(),
        d.title(),
        d.body(),
        d.sourceHash(),
        d.visible(),
        d.revision(),
        Timestamp.from(now),
        d.type(),
        d.id());
  }

  private static final String VISIBLE =
      " FROM campus_search_document p JOIN campus_search_source s ON"
          + " s.resource_type=p.resource_type AND s.resource_id=p.resource_id WHERE p.visible=TRUE"
          + " AND p.title=s.title AND p.body=s.body AND p.school_id=s.school_id AND"
          + " p.merchant_id<=>s.merchant_id AND (? IS NULL OR s.resource_type=?) AND (? IS NULL OR"
          + " s.school_id=?) AND (INSTR(s.title,?)>0 OR INSTR(s.body,?)>0) ";

  public List<SearchSource> query(
      String keyword, String type, String school, int size, long offset) {
    return jdbc.query(
        "SELECT s.*" + VISIBLE + " ORDER BY s.resource_type,s.resource_id LIMIT ? OFFSET ?",
        SOURCE,
        type,
        type,
        school,
        school,
        keyword,
        keyword,
        size,
        offset);
  }

  public long count(String keyword, String type, String school) {
    return jdbc.queryForObject(
        "SELECT COUNT(*)" + VISIBLE, Long.class, type, type, school, school, keyword, keyword);
  }

  public boolean consumed(String event) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM campus_consumed_event WHERE consumer_name='campus-search' AND"
                + " event_id=?",
            Integer.class,
            event)
        > 0;
  }

  public void consume(String event, Instant now) {
    jdbc.update(
        "INSERT INTO campus_consumed_event(consumer_name,event_id,consumed_at)"
            + " VALUES('campus-search',?,?) ON DUPLICATE KEY UPDATE event_id=event_id",
        event,
        Timestamp.from(now));
  }

  public void rebuild(String id, long actor, Instant now) {
    jdbc.update(
        "INSERT INTO campus_search_rebuild(id,requested_by,created_at) VALUES(?,?,?)",
        id,
        actor,
        Timestamp.from(now));
  }

  public Rebuild rebuild(String id, boolean lock) {
    var values =
        jdbc.query(
            "SELECT * FROM campus_search_rebuild WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            REBUILD,
            id);
    return values.isEmpty() ? null : values.get(0);
  }

  public Rebuild activeRebuild() {
    var values =
        jdbc.query(
            "SELECT * FROM campus_search_rebuild WHERE status IN ('PENDING','RUNNING') LIMIT 1",
            REBUILD);
    return values.isEmpty() ? null : values.get(0);
  }

  public List<Rebuild> rebuilds(int size, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_search_rebuild ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
        REBUILD,
        size,
        offset);
  }

  public long rebuildCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM campus_search_rebuild", Long.class);
  }

  public record Job(Rebuild value, String token) {}

  public Job lease(Instant now, Instant until, String token) {
    var rows =
        jdbc.query(
            "SELECT * FROM campus_search_rebuild WHERE status='PENDING' OR (status='RUNNING' AND"
                + " locked_until<=?) ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
            REBUILD,
            Timestamp.from(now));
    if (rows.isEmpty()) return null;
    var r = rows.get(0);
    jdbc.update(
        "UPDATE campus_search_rebuild SET status='RUNNING',lease_token=?,locked_until=? WHERE id=?",
        token,
        Timestamp.from(until),
        r.id());
    return new Job(r, token);
  }

  public List<Key> keys(String type, String id, int limit) {
    return jdbc.query(
        "SELECT resource_type,resource_id FROM (SELECT resource_type,resource_id FROM"
            + " campus_search_document UNION SELECT resource_type,resource_id FROM"
            + " campus_search_source) k WHERE (resource_type>?) OR (resource_type=? AND"
            + " resource_id>?) ORDER BY resource_type,resource_id LIMIT ?",
        (r, n) -> new Key(r.getString(1), r.getString(2), null),
        type,
        type,
        id,
        limit);
  }

  public void advance(Job job, Key last, int count, boolean done, Instant now) {
    jdbc.update(
        "UPDATE campus_search_rebuild SET"
            + " status=?,cursor_type=?,cursor_id=?,processed=processed+?,lease_token=NULL,locked_until=NULL,completed_at=?"
            + " WHERE id=? AND lease_token=? AND status='RUNNING'",
        done ? "COMPLETED" : "PENDING",
        last == null ? job.value().cursorType() : last.type(),
        last == null ? job.value().cursorId() : last.id(),
        count,
        done ? Timestamp.from(now) : null,
        job.value().id(),
        job.token());
  }

  public void fail(Job job) {
    jdbc.update(
        "UPDATE campus_search_rebuild SET"
            + " status='FAILED',error_code='INDEX_UNAVAILABLE',lease_token=NULL,locked_until=NULL"
            + " WHERE id=? AND lease_token=? AND status='RUNNING'",
        job.value().id(),
        job.token());
  }

  public void retry(String id) {
    jdbc.update(
        "UPDATE campus_search_rebuild SET status='PENDING',error_code=NULL WHERE id=? AND"
            + " status='FAILED'",
        id);
  }
}
