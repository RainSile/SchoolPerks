package com.campus.perks.user.mapper;

import com.campus.perks.user.vo.RbacViews.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class RbacMapper {
  private final JdbcTemplate db;

  public RbacMapper(JdbcTemplate db) {
    this.db = db;
  }

  public void serialize() {
    db.queryForObject("SELECT id FROM campus_rbac_lock WHERE id=1 FOR UPDATE", Integer.class);
  }

  public boolean canManage(long id) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM campus_user u JOIN campus_user_role ur ON ur.user_id=u.id JOIN"
                + " campus_role r ON r.id=ur.role_id JOIN campus_role_permission rp ON"
                + " rp.role_id=r.id JOIN campus_permission p ON p.id=rp.permission_id WHERE u.id=?"
                + " AND u.account_type='ADMIN' AND u.status='ACTIVE' AND r.account_type='ADMIN' AND"
                + " p.code IN ('rbac:manage','business:manage')",
            Integer.class,
            id)
        > 0;
  }

  private Role role(java.sql.ResultSet r, int row) throws java.sql.SQLException {
    return new Role(
        r.getString("id"),
        r.getString("code"),
        r.getString("name"),
        r.getString("account_type"),
        r.getInt("version"),
        db.queryForList(
            "SELECT p.code FROM campus_permission p JOIN campus_role_permission rp ON"
                + " rp.permission_id=p.id WHERE rp.role_id=? ORDER BY p.code",
            String.class,
            r.getLong("id")));
  }

  private Account account(java.sql.ResultSet r, int row) throws java.sql.SQLException {
    return new Account(
        r.getString("id"),
        r.getString("nickname"),
        r.getString("account_type"),
        r.getString("status"),
        r.getInt("access_version"),
        db.queryForList(
            "SELECT CAST(role_id AS CHAR) FROM campus_user_role WHERE user_id=? ORDER BY role_id",
            String.class,
            r.getLong("id")));
  }

  public List<Role> roles(int size, long offset) {
    return db.query(
        "SELECT * FROM campus_role ORDER BY id LIMIT ? OFFSET ?", this::role, size, offset);
  }

  public Role role(long id, boolean lock) {
    var rows =
        db.query(
            "SELECT * FROM campus_role WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::role, id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public Account account(long id, boolean lock) {
    var rows =
        db.query(
            "SELECT * FROM campus_user WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            this::account,
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public List<Account> accounts(int size, long offset) {
    return db.query(
        "SELECT * FROM campus_user ORDER BY id LIMIT ? OFFSET ?", this::account, size, offset);
  }

  public long countRoles() {
    return db.queryForObject("SELECT COUNT(*) FROM campus_role", Long.class);
  }

  public long countAccounts() {
    return db.queryForObject("SELECT COUNT(*) FROM campus_user", Long.class);
  }

  public List<Permission> permissions() {
    return db.query(
        "SELECT code,name FROM campus_permission ORDER BY code",
        (r, n) -> new Permission(r.getString(1), r.getString(2)));
  }

  public long create(String code, String name, String type) {
    var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
    db.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO campus_role(code,name,account_type) VALUES(?,?,?)",
                  java.sql.Statement.RETURN_GENERATED_KEYS);
          p.setString(1, code);
          p.setString(2, name);
          p.setString(3, type);
          return p;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public void updateRole(long id, String name) {
    db.update("UPDATE campus_role SET name=?,version=version+1 WHERE id=?", name, id);
  }

  public void permissions(long id, List<String> values) {
    db.update("DELETE FROM campus_role_permission WHERE role_id=?", id);
    for (String code : values)
      db.update(
          "INSERT INTO campus_role_permission(role_id,permission_id) SELECT ?,id FROM"
              + " campus_permission WHERE code=?",
          id,
          code);
  }

  public boolean assignedTo(long role, long user) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM campus_user_role WHERE role_id=? AND user_id=?",
            Integer.class,
            role,
            user)
        > 0;
  }

  public List<Long> roleUsers(long role) {
    return db.queryForList(
        "SELECT user_id FROM campus_user_role WHERE role_id=? ORDER BY user_id", Long.class, role);
  }

  public boolean hasMembership(long user) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM campus_merchant_member WHERE user_id=?", Integer.class, user)
        > 0;
  }

  public void updateAccount(long id, String type, String status) {
    db.update(
        "UPDATE campus_user SET account_type=?,status=?,access_version=access_version+1 WHERE id=?",
        type,
        status,
        id);
  }

  public void revoke(long id) {
    db.update(
        "UPDATE campus_auth_session SET revoked_at=UTC_TIMESTAMP(3) WHERE user_id=? AND revoked_at"
            + " IS NULL",
        id);
  }

  public void invalidate(long id) {
    db.update("UPDATE campus_user SET access_version=access_version+1 WHERE id=?", id);
    revoke(id);
  }

  public void grants(long user, List<Long> roles) {
    db.update("DELETE FROM campus_user_role WHERE user_id=?", user);
    for (long role : roles)
      db.update("INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", user, role);
  }

  public void deleteRole(long id) {
    db.update("DELETE FROM campus_role_permission WHERE role_id=?", id);
    db.update("DELETE FROM campus_role WHERE id=?", id);
  }
}
