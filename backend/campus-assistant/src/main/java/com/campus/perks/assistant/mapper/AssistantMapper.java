package com.campus.perks.assistant.mapper;

import com.campus.perks.assistant.entity.AssistantRows.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AssistantMapper {
  private final JdbcTemplate db;

  public AssistantMapper(JdbcTemplate db) {
    this.db = db;
  }

  private static final RowMapper<Turn> TURN =
      (r, n) ->
          new Turn(
              r.getString("id"),
              r.getString("conversation_id"),
              r.getString("request_key"),
              r.getString("input_hash"),
              r.getString("input_text"),
              r.getString("status"),
              r.getString("lease_token"),
              r.getTimestamp("locked_until") == null
                  ? null
                  : r.getTimestamp("locked_until").toInstant(),
              r.getString("error_code"),
              r.getTimestamp("created_at").toInstant(),
              r.getInt("attempt"));

  public void create(String id, String owner, String portal, Instant now) {
    db.update(
        "INSERT INTO campus_assistant_conversation(id,owner_id,account_type,created_at)"
            + " VALUES(?,?,?,?)",
        id,
        owner,
        portal,
        Timestamp.from(now));
  }

  public Conversation conversation(String id, boolean lock) {
    var rows =
        db.query(
            "SELECT * FROM campus_assistant_conversation WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) ->
                new Conversation(
                    r.getString("id"),
                    r.getString("owner_id"),
                    r.getString("account_type"),
                    r.getTimestamp("created_at").toInstant()),
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public Turn turn(String id) {
    var rows = db.query("SELECT * FROM campus_assistant_turn WHERE id=?", TURN, id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public Turn requested(String conversation, String key) {
    var rows =
        db.query(
            "SELECT * FROM campus_assistant_turn WHERE conversation_id=? AND request_key=? FOR"
                + " UPDATE",
            TURN,
            conversation,
            key);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public void createTurn(
      String id, String conversation, String key, String hash, String input, Instant now) {
    db.update(
        "INSERT INTO"
            + " campus_assistant_turn(id,conversation_id,request_key,input_hash,input_text,created_at)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        conversation,
        key,
        hash,
        input,
        Timestamp.from(now));
  }

  public void claim(String id, String token, Instant until) {
    db.update(
        "UPDATE campus_assistant_turn SET"
            + " status='RUNNING',lease_token=?,locked_until=?,error_code=NULL,attempt=attempt+1"
            + " WHERE id=?",
        token,
        Timestamp.from(until),
        id);
  }

  public void expire(String conversation, Instant now) {
    db.update(
        "UPDATE campus_assistant_turn SET"
            + " status='FAILED',lease_token=NULL,locked_until=NULL,error_code='LEASE_EXPIRED' WHERE"
            + " conversation_id=? AND status='RUNNING' AND locked_until<=?",
        conversation,
        Timestamp.from(now));
  }

  public boolean busy(String conversation, Instant now) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM campus_assistant_turn WHERE conversation_id=? AND"
                + " status='RUNNING' AND locked_until>?",
            Integer.class,
            conversation,
            Timestamp.from(now))
        > 0;
  }

  public boolean complete(String id, String token, Instant now) {
    return db.update(
            "UPDATE campus_assistant_turn SET"
                + " status='COMPLETED',input_text=NULL,lease_token=NULL,locked_until=NULL,completed_at=?"
                + " WHERE id=? AND lease_token=? AND status='RUNNING'",
            Timestamp.from(now),
            id,
            token)
        == 1;
  }

  public void fail(String id, String token, String error) {
    db.update(
        "UPDATE campus_assistant_turn SET"
            + " status='FAILED',lease_token=NULL,locked_until=NULL,error_code=? WHERE id=? AND"
            + " lease_token=? AND status='RUNNING'",
        error,
        id,
        token);
  }
}
