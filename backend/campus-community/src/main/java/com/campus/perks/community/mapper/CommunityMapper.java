package com.campus.perks.community.mapper;

import com.campus.perks.community.entity.CommunityRows.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class CommunityMapper implements com.campus.perks.community.service.CommunityInteractions {
  private final JdbcTemplate jdbc;

  public String latestReason(long id, String status) {
    return one(
        "SELECT reason FROM campus_community_moderation WHERE resource_type='POST' AND"
            + " resource_id=? AND status=? ORDER BY created_at DESC,id DESC LIMIT 1",
        (r, n) -> r.getString(1),
        id,
        status);
  }

  public CommunityMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant time(ResultSet r, String key) throws SQLException {
    var t = r.getTimestamp(key);
    return t == null ? null : t.toInstant();
  }

  private static final RowMapper<Post> POST =
      (r, n) ->
          new Post(
              r.getLong("id"),
              r.getLong("author_id"),
              r.getLong("school_id"),
              (Long) r.getObject("merchant_id"),
              r.getString("title"),
              r.getString("body"),
              r.getString("status"),
              r.getInt("version"),
              time(r, "created_at"),
              time(r, "published_at"));
  private static final RowMapper<Review> REVIEW =
      (r, n) ->
          new Review(
              r.getString("id"),
              r.getLong("post_id"),
              r.getInt("content_version"),
              r.getString("snapshot"),
              r.getString("status"),
              (Long) r.getObject("reviewer_id"),
              r.getString("reason"),
              time(r, "created_at"),
              time(r, "reviewed_at"));
  private static final RowMapper<Comment> COMMENT =
      (r, n) ->
          new Comment(
              r.getLong("id"),
              r.getLong("post_id"),
              r.getLong("author_id"),
              r.getString("request_key"),
              r.getString("body"),
              r.getString("status"),
              r.getInt("version"),
              time(r, "created_at"));
  private static final RowMapper<Report> REPORT =
      (r, n) ->
          new Report(
              r.getLong("id"),
              r.getLong("post_id"),
              r.getLong("reporter_id"),
              r.getString("reason"),
              r.getString("status"),
              r.getInt("version"),
              r.getString("resolution"),
              time(r, "created_at"));

  private <T> T one(String sql, RowMapper<T> mapper, Object... args) {
    var rows = jdbc.query(sql, mapper, args);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++)
            p.setObject(i + 1, args[i] instanceof Instant t ? Timestamp.from(t) : args[i]);
          return p;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  public Post post(long id, boolean lock) {
    return one("SELECT * FROM campus_post WHERE id=?" + (lock ? " FOR UPDATE" : ""), POST, id);
  }

  public Post visible(long id) {
    return one("SELECT * FROM campus_post_catalog WHERE id=?", POST, id);
  }

  public Post visibleCurrent(long id) {
    return one("SELECT * FROM campus_post_catalog WHERE id=? FOR SHARE", POST, id);
  }

  public List<Post> catalog(String keyword, String school, int size, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_post_catalog WHERE (? IS NULL OR school_id=?) AND (? IS NULL OR"
            + " INSTR(title,?)>0 OR INSTR(body,?)>0) ORDER BY published_at DESC,id DESC LIMIT ?"
            + " OFFSET ?",
        POST,
        school,
        school,
        keyword,
        keyword,
        keyword,
        size,
        offset);
  }

  public long catalogCount(String keyword, String school) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_post_catalog WHERE (? IS NULL OR school_id=?) AND (? IS NULL"
            + " OR INSTR(title,?)>0 OR INSTR(body,?)>0)",
        Long.class,
        school,
        school,
        keyword,
        keyword,
        keyword);
  }

  public List<Post> own(long actor, int size, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_post WHERE author_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
        POST,
        actor,
        size,
        offset);
  }

  public long ownCount(long actor) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_post WHERE author_id=?", Long.class, actor);
  }

  public long create(
      long actor, long school, Long merchant, String title, String body, Instant now) {
    return insert(
        "INSERT INTO campus_post(author_id,school_id,merchant_id,title,body,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,?)",
        actor,
        school,
        merchant,
        title,
        body,
        now,
        now);
  }

  public void edit(long id, String title, String body, Instant now) {
    jdbc.update(
        "UPDATE campus_post SET title=?,body=?,status='DRAFT',version=version+1,updated_at=? WHERE"
            + " id=?",
        title,
        body,
        Timestamp.from(now),
        id);
  }

  public void status(long id, String status, Instant now) {
    jdbc.update(
        "UPDATE campus_post SET status=?,version=version+1,updated_at=?,published_at=CASE WHEN"
            + " ?='PUBLISHED' THEN ? ELSE published_at END WHERE id=?",
        status,
        Timestamp.from(now),
        status,
        Timestamp.from(now),
        id);
  }

  public List<String> images(long post) {
    return jdbc.queryForList(
        "SELECT file_id FROM campus_post_image WHERE post_id=? ORDER BY position",
        String.class,
        post);
  }

  public void images(long post, List<String> ids) {
    jdbc.update("DELETE FROM campus_post_image WHERE post_id=?", post);
    for (int i = 0; i < ids.size(); i++)
      jdbc.update(
          "INSERT INTO campus_post_image(post_id,file_id,position) VALUES(?,?,?)",
          post,
          ids.get(i),
          i);
  }

  public void submit(String id, Post post, String snapshot, Instant now) {
    jdbc.update(
        "INSERT INTO campus_post_review_request(id,post_id,content_version,snapshot,created_at)"
            + " VALUES(?,?,?,?,?)",
        id,
        post.id(),
        post.version(),
        snapshot,
        Timestamp.from(now));
  }

  public Review review(String id, boolean lock) {
    return one(
        "SELECT * FROM campus_post_review_request WHERE id=?" + (lock ? " FOR UPDATE" : ""),
        REVIEW,
        id);
  }

  public List<Review> reviews(String status, int size, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_post_review_request WHERE (? IS NULL OR status=?) ORDER BY"
            + " created_at,id LIMIT ? OFFSET ?",
        REVIEW,
        status,
        status,
        size,
        offset);
  }

  public long reviewCount(String status) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_post_review_request WHERE (? IS NULL OR status=?)",
        Long.class,
        status,
        status);
  }

  public void decide(String id, String status, long actor, String reason, Instant now) {
    jdbc.update(
        "UPDATE campus_post_review_request SET status=?,reviewer_id=?,reason=?,reviewed_at=? WHERE"
            + " id=?",
        status,
        actor,
        reason,
        Timestamp.from(now),
        id);
  }

  public void withdrawReviews(long post, long actor, Instant now) {
    jdbc.update(
        "UPDATE campus_post_review_request SET"
            + " status='REJECTED',reviewer_id=?,reason='AUTHOR_DELETED',reviewed_at=? WHERE"
            + " post_id=? AND status='PENDING'",
        actor,
        Timestamp.from(now),
        post);
  }

  public Comment comment(long id, boolean lock) {
    return one(
        "SELECT * FROM campus_post_comment WHERE id=?" + (lock ? " FOR UPDATE" : ""), COMMENT, id);
  }

  public Comment requestedComment(long post, long actor, String key) {
    return one(
        "SELECT * FROM campus_post_comment WHERE post_id=? AND author_id=? AND request_key=? FOR"
            + " UPDATE",
        COMMENT,
        post,
        actor,
        key);
  }

  public long addComment(long post, long actor, String key, String body, Instant now) {
    return insert(
        "INSERT INTO campus_post_comment(post_id,author_id,request_key,body,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?)",
        post,
        actor,
        key,
        body,
        now,
        now);
  }

  private static final String PUBLIC_COMMENT =
      " FROM campus_post_comment c JOIN campus_user u ON u.id=c.author_id WHERE c.post_id=? AND"
          + " c.status='APPROVED' AND u.status='ACTIVE' ";

  public List<Comment> comments(long post, int size, long offset) {
    return jdbc.query(
        "SELECT c.*" + PUBLIC_COMMENT + " ORDER BY c.id LIMIT ? OFFSET ?",
        COMMENT,
        post,
        size,
        offset);
  }

  public long commentCount(long post) {
    return jdbc.queryForObject("SELECT COUNT(*)" + PUBLIC_COMMENT, Long.class, post);
  }

  public List<Comment> ownComments(long actor, int size, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_post_comment WHERE author_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
        COMMENT,
        actor,
        size,
        offset);
  }

  public long ownCommentCount(long actor) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_post_comment WHERE author_id=?", Long.class, actor);
  }

  public List<Comment> commentQueue(String status, int size, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_post_comment WHERE (? IS NULL OR status=?) ORDER BY id LIMIT ? OFFSET"
            + " ?",
        COMMENT,
        status,
        status,
        size,
        offset);
  }

  public long commentQueueCount(String status) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_post_comment WHERE (? IS NULL OR status=?)",
        Long.class,
        status,
        status);
  }

  public void commentStatus(long id, String status, Instant now) {
    jdbc.update(
        "UPDATE campus_post_comment SET status=?,version=version+1,updated_at=? WHERE id=?",
        status,
        Timestamp.from(now),
        id);
  }

  public long likeCount(long post) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_post_like WHERE post_id=?", Long.class, post);
  }

  public boolean liked(long post, long actor) {
    return one(
            "SELECT user_id FROM campus_post_like WHERE post_id=? AND user_id=? FOR UPDATE",
            (r, n) -> r.getLong(1),
            post,
            actor)
        != null;
  }

  public void like(long post, long actor, Instant now) {
    jdbc.update(
        "INSERT INTO campus_post_like(post_id,user_id,created_at) VALUES(?,?,?)",
        post,
        actor,
        Timestamp.from(now));
  }

  public void unlike(long post, long actor) {
    jdbc.update("DELETE FROM campus_post_like WHERE post_id=? AND user_id=?", post, actor);
  }

  public Report report(long id, boolean lock) {
    return one(
        "SELECT * FROM campus_post_report WHERE id=?" + (lock ? " FOR UPDATE" : ""), REPORT, id);
  }

  public Report ownReport(long post, long actor) {
    return one(
        "SELECT * FROM campus_post_report WHERE post_id=? AND reporter_id=? FOR UPDATE",
        REPORT,
        post,
        actor);
  }

  public long report(long post, long actor, String reason, Instant now) {
    return insert(
        "INSERT INTO campus_post_report(post_id,reporter_id,reason,created_at) VALUES(?,?,?,?)",
        post,
        actor,
        reason,
        now);
  }

  public List<Report> reports(String status, int size, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_post_report WHERE (? IS NULL OR status=?) ORDER BY id LIMIT ? OFFSET"
            + " ?",
        REPORT,
        status,
        status,
        size,
        offset);
  }

  public long reportCount(String status) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_post_report WHERE (? IS NULL OR status=?)",
        Long.class,
        status,
        status);
  }

  public void resolve(long id, String status, long actor, String reason, Instant now) {
    jdbc.update(
        "UPDATE campus_post_report SET"
            + " status=?,version=version+1,resolver_id=?,resolution=?,resolved_at=? WHERE id=?",
        status,
        actor,
        reason,
        Timestamp.from(now),
        id);
  }

  public void moderation(
      String id,
      String type,
      long resource,
      long actor,
      String previous,
      String status,
      String reason,
      Instant now) {
    jdbc.update(
        "INSERT INTO"
            + " campus_community_moderation(id,resource_type,resource_id,actor_id,previous_status,status,reason,created_at)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        id,
        type,
        resource,
        actor,
        previous,
        status,
        reason,
        Timestamp.from(now));
  }
}
