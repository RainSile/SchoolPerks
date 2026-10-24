package com.campus.perks.user.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.*;
import com.campus.perks.user.dto.RbacRequests.*;
import com.campus.perks.user.mapper.RbacMapper;
import com.campus.perks.user.service.RbacService;
import com.campus.perks.user.vo.RbacViews.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class RbacServiceImpl implements RbacService {
  private final RbacMapper db;
  private final AccountDirectoryPort accounts;
  private final AuditPort audit;

  public RbacServiceImpl(RbacMapper db, AccountDirectoryPort accounts, AuditPort audit) {
    this.db = db;
    this.accounts = accounts;
    this.audit = audit;
  }

  private void allowed() {
    CurrentActor.requirePortal(AccountPortal.ADMIN);
    CurrentActor.require("rbac:manage");
    if (org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive()) db.serialize();
  }

  private ApplicationException error(HttpStatus s, String code, String detail) {
    return new ApplicationException(s, code, detail);
  }

  private long id(String id) {
    if (id == null || !id.matches("[1-9][0-9]{0,17}"))
      throw error(HttpStatus.NOT_FOUND, "RBAC_RESOURCE_NOT_FOUND", "角色或账号不存在");
    return Long.parseLong(id);
  }

  private void lockUsers(Collection<Long> users) {
    long actor = Long.parseLong(CurrentActor.id());
    var sorted = new TreeSet<Long>(users);
    sorted.add(actor);
    sorted.forEach(
        u -> {
          if (u == actor) accounts.lockActiveUser(u.toString());
          else accounts.lockExistingUser(u.toString());
        });
    if (!db.canManage(actor)) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前权限已变更");
  }

  private List<String> checked(String type, List<String> permissions) {
    var unique = new TreeSet<>(permissions);
    if (unique.size() != permissions.size())
      throw error(HttpStatus.UNPROCESSABLE_ENTITY, "DUPLICATE_PERMISSION", "权限不能重复");
    var catalogue = new HashSet<>(db.permissions().stream().map(Permission::code).toList());
    if (!catalogue.containsAll(unique) || (!"ADMIN".equals(type) && !unique.isEmpty()))
      throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ROLE_PERMISSION", "权限不存在或与角色所属端不一致");
    return List.copyOf(unique);
  }

  public PageResponse<Role> roles(PageQuery q) {
    allowed();
    return new PageResponse<>(
        db.roles(q.getSize(), (long) (q.getPage() - 1) * q.getSize()),
        db.countRoles(),
        q.getPage(),
        q.getSize());
  }

  public List<Permission> permissions() {
    allowed();
    return db.permissions();
  }

  public Role role(String id) {
    allowed();
    return requireRole(id, false);
  }

  private Role requireRole(String id, boolean lock) {
    var r = db.role(id(id), lock);
    if (r == null) throw error(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "角色不存在");
    return r;
  }

  private void version(int actual, int requested) {
    if (actual != requested) throw error(HttpStatus.CONFLICT, "STALE_VERSION", "授权已变更，请刷新后重试");
  }

  @Transactional
  public Role create(RoleInput r) {
    allowed();
    lockUsers(List.of());
    var permissions = checked(r.accountType().name(), r.permissions());
    long key;
    try {
      key = db.create(r.code(), r.name().strip(), r.accountType().name());
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw error(HttpStatus.CONFLICT, "ROLE_CODE_EXISTS", "角色代码已存在");
    }
    db.permissions(key, permissions);
    audit.record(
        CurrentActor.id(),
        "ROLE_CREATED",
        "ROLE",
        "" + key,
        Map.of("permissionCount", permissions.size()));
    return db.role(key, false);
  }

  @Transactional
  public Role update(String id, RoleUpdate r) {
    allowed();
    long key = id(id);
    var users = db.roleUsers(key);
    lockUsers(users);
    var role = requireRole(id, true);
    version(role.version(), r.version());
    if (db.assignedTo(key, Long.parseLong(CurrentActor.id())))
      throw error(HttpStatus.CONFLICT, "SELF_AUTHORIZATION_FORBIDDEN", "不能修改自己持有的角色，请由其他授权管理员处理");
    var permissions = checked(role.accountType(), r.permissions());
    db.updateRole(key, r.name().strip());
    db.permissions(key, permissions);
    users.forEach(db::invalidate);
    audit.record(
        CurrentActor.id(),
        "ROLE_UPDATED",
        "ROLE",
        id,
        Map.of("permissionCount", permissions.size()));
    return db.role(key, false);
  }

  @Transactional
  public void delete(String id, int version) {
    allowed();
    lockUsers(List.of());
    var role = requireRole(id, true);
    version(role.version(), version);
    if (!db.roleUsers(id(id)).isEmpty())
      throw error(HttpStatus.CONFLICT, "ROLE_IN_USE", "角色仍有关联账号，不能删除");
    db.deleteRole(id(id));
    audit.record(CurrentActor.id(), "ROLE_DELETED", "ROLE", id, Map.of());
  }

  public PageResponse<Account> accounts(PageQuery q) {
    allowed();
    return new PageResponse<>(
        db.accounts(q.getSize(), (long) (q.getPage() - 1) * q.getSize()),
        db.countAccounts(),
        q.getPage(),
        q.getSize());
  }

  public Account account(String id) {
    allowed();
    var a = db.account(id(id), false);
    if (a == null) throw error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在");
    return a;
  }

  private Account target(String id, int version) {
    if (CurrentActor.id().equals(id))
      throw error(HttpStatus.CONFLICT, "SELF_AUTHORIZATION_FORBIDDEN", "不能修改自己的账号授权");
    long key = id(id);
    lockUsers(List.of(key));
    var a = db.account(key, true);
    if (a == null) throw error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在");
    version(a.version(), version);
    return a;
  }

  @Transactional
  public Account updateAccount(String id, AccountUpdate r) {
    allowed();
    var a = target(id, r.version());
    if (!a.accountType().equals(r.accountType().name()) && db.hasMembership(id(id)))
      throw error(HttpStatus.CONFLICT, "MERCHANT_MEMBERSHIP_EXISTS", "账号仍有关联门店，不能变更所属端");
    if (!a.accountType().equals(r.accountType().name())) db.grants(id(id), List.of());
    db.updateAccount(id(id), r.accountType().name(), r.status().name());
    db.revoke(id(id));
    audit.record(
        CurrentActor.id(),
        "ACCOUNT_ACCESS_UPDATED",
        "USER",
        id,
        Map.of("accountType", r.accountType().name(), "status", r.status().name()));
    return db.account(id(id), false);
  }

  @Transactional
  public Account grants(String id, Grants r) {
    allowed();
    var a = target(id, r.version());
    var roles = r.roleIds().stream().map(this::id).sorted().distinct().toList();
    if (roles.size() != r.roleIds().size())
      throw error(HttpStatus.UNPROCESSABLE_ENTITY, "DUPLICATE_ROLE", "角色不能重复");
    for (long key : roles) {
      var role = requireRole("" + key, true);
      if (!role.accountType().equals(a.accountType()))
        throw error(HttpStatus.UNPROCESSABLE_ENTITY, "ROLE_PORTAL_MISMATCH", "角色与账号所属端不一致");
    }
    db.grants(id(id), roles);
    db.invalidate(id(id));
    audit.record(
        CurrentActor.id(), "ACCOUNT_ROLES_UPDATED", "USER", id, Map.of("roleCount", roles.size()));
    return db.account(id(id), false);
  }
}
