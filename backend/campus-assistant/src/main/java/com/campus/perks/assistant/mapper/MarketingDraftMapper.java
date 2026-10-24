package com.campus.perks.assistant.mapper;

import com.campus.perks.assistant.entity.MarketingDraftRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class MarketingDraftMapper {
  private final JdbcTemplate db;

  public MarketingDraftMapper(JdbcTemplate db) {
    this.db = db;
  }

  private static final RowMapper<MarketingDraftRow> ROW =
      (r, n) ->
          new MarketingDraftRow(
              r.getString("id"),
              r.getString("merchant_id"),
              r.getString("created_by"),
              r.getString("request_key"),
              r.getString("input_hash"),
              r.getString("brief"),
              r.getString("tone"),
              r.getString("coupon_id"),
              r.getString("status"),
              r.getString("title"),
              r.getString("body"),
              r.getString("source_snapshot"),
              r.getString("source_hash"),
              r.getInt("version"),
              r.getInt("attempt"),
              r.getString("lease_token"),
              r.getTimestamp("locked_until") == null
                  ? null
                  : r.getTimestamp("locked_until").toInstant(),
              r.getString("error_code"),
              r.getTimestamp("created_at").toInstant(),
              r.getTimestamp("updated_at").toInstant());

  public MarketingDraftRow get(String id, boolean lock) {
    var rows =
        db.query(
            "SELECT * FROM campus_marketing_draft WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            ROW,
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public MarketingDraftRow requested(String merchant, String actor, String key) {
    var rows =
        db.query(
            "SELECT * FROM campus_marketing_draft WHERE merchant_id=? AND created_by=? AND"
                + " request_key=? FOR UPDATE",
            ROW,
            merchant,
            actor,
            key);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public void create(
      String id,
      String merchant,
      String actor,
      String key,
      String hash,
      String brief,
      String tone,
      String coupon,
      Instant now) {
    db.update(
        "INSERT INTO"
            + " campus_marketing_draft(id,merchant_id,created_by,request_key,input_hash,brief,tone,coupon_id,status,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,?,?,'FAILED',?,?)",
        id,
        merchant,
        actor,
        key,
        hash,
        brief,
        tone,
        coupon,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  public void claim(
      String id, String token, String snapshot, String hash, Instant until, Instant now) {
    db.update(
        "UPDATE campus_marketing_draft SET"
            + " status='GENERATING',lease_token=?,locked_until=?,source_snapshot=?,source_hash=?,attempt=attempt+1,error_code=NULL,version=version+1,updated_at=?"
            + " WHERE id=?",
        token,
        Timestamp.from(until),
        snapshot,
        hash,
        Timestamp.from(now),
        id);
  }

  public boolean complete(String id, String token, String title, String body, Instant now) {
    return db.update(
            "UPDATE campus_marketing_draft SET"
                + " status='DRAFT',title=?,body=?,lease_token=NULL,locked_until=NULL,version=version+1,updated_at=?"
                + " WHERE id=? AND status='GENERATING' AND lease_token=? AND locked_until>?",
            title,
            body,
            Timestamp.from(now),
            id,
            token,
            Timestamp.from(now))
        == 1;
  }

  public void fail(String id, String token, String code, Instant now) {
    db.update(
        "UPDATE campus_marketing_draft SET"
            + " status='FAILED',error_code=?,lease_token=NULL,locked_until=NULL,version=version+1,updated_at=?"
            + " WHERE id=? AND status='GENERATING' AND lease_token=?",
        code,
        Timestamp.from(now),
        id,
        token);
  }

  public void edit(String id, String title, String body, Instant now) {
    db.update(
        "UPDATE campus_marketing_draft SET title=?,body=?,version=version+1,updated_at=? WHERE"
            + " id=?",
        title,
        body,
        Timestamp.from(now),
        id);
  }

  public void archive(String id, Instant now) {
    db.update(
        "UPDATE campus_marketing_draft SET"
            + " status='ARCHIVED',lease_token=NULL,locked_until=NULL,version=version+1,updated_at=?"
            + " WHERE id=?",
        Timestamp.from(now),
        id);
  }

  public List<MarketingDraftRow> list(String merchant, int size, long offset) {
    return db.query(
        "SELECT * FROM campus_marketing_draft WHERE merchant_id=? ORDER BY created_at DESC,id DESC"
            + " LIMIT ? OFFSET ?",
        ROW,
        merchant,
        size,
        offset);
  }

  public long count(String merchant) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM campus_marketing_draft WHERE merchant_id=?", Long.class, merchant);
  }
}
