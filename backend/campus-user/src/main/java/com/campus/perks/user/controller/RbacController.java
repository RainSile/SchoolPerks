package com.campus.perks.user.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.user.dto.RbacRequests.*;
import com.campus.perks.user.service.RbacService;
import com.campus.perks.user.vo.RbacViews.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
@Tag(name = "角色与账号权限管理")
@SecurityRequirement(name = "bearerAuth")
public class RbacController {
  private final ObjectProvider<RbacService> provider;

  public RbacController(ObjectProvider<RbacService> provider) {
    this.provider = provider;
  }

  private RbacService service() {
    var s = provider.getIfAvailable();
    if (s == null)
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "RBAC_UNAVAILABLE", "权限管理尚未启用");
    return s;
  }

  @GetMapping("/permissions")
  @Operation(summary = "rbac:manage 查询可分配权限目录")
  public List<Permission> permissions() {
    return service().permissions();
  }

  @GetMapping("/roles")
  @Operation(summary = "rbac:manage 查询角色分页及所属端")
  public PageResponse<Role> roles(@Valid @ParameterObject PageQuery q) {
    return service().roles(q);
  }

  @GetMapping("/roles/{id}")
  @Operation(summary = "rbac:manage 角色及权限详情")
  public Role role(@PathVariable String id) {
    return service().role(id);
  }

  @PostMapping("/roles")
  @com.campus.perks.security.service.AuditedOperation(
      action = "RBAC_ROLE_CREATE",
      resourceType = "ROLE")
  @Operation(summary = "rbac:manage 创建所属端固定的角色")
  public ResponseEntity<Role> create(@Valid @RequestBody RoleInput r) {
    var role = service().create(r);
    return ResponseEntity.created(URI.create("/api/v1/admin/roles/" + role.id())).body(role);
  }

  @PatchMapping("/roles/{id}")
  @com.campus.perks.security.service.AuditedOperation(
      action = "RBAC_ROLE_UPDATE",
      resourceType = "ROLE",
      resourceArgument = 0)
  @Operation(summary = "rbac:manage 按版本修改角色，撤销受影响账号旧会话")
  public Role update(@PathVariable String id, @Valid @RequestBody RoleUpdate r) {
    return service().update(id, r);
  }

  @DeleteMapping("/roles/{id}")
  @com.campus.perks.security.service.AuditedOperation(
      action = "RBAC_ROLE_DELETE",
      resourceType = "ROLE",
      resourceArgument = 0)
  @Operation(summary = "rbac:manage 按版本删除未被使用的角色")
  public ResponseEntity<Void> delete(@PathVariable String id, @RequestParam @Min(0) int version) {
    service().delete(id, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/accounts")
  @Operation(summary = "rbac:manage 查询账号所属端、状态与角色，不返回手机号")
  public PageResponse<Account> accounts(@Valid @ParameterObject PageQuery q) {
    return service().accounts(q);
  }

  @GetMapping("/accounts/{id}")
  @Operation(summary = "rbac:manage 单个账号授权详情")
  public Account account(@PathVariable String id) {
    return service().account(id);
  }

  @PatchMapping("/accounts/{id}")
  @com.campus.perks.security.service.AuditedOperation(
      action = "RBAC_ACCOUNT_UPDATE",
      resourceType = "ACCOUNT",
      resourceArgument = 0)
  @Operation(summary = "rbac:manage 修改他人账号所属端或状态，并撤销旧会话")
  public Account updateAccount(@PathVariable String id, @Valid @RequestBody AccountUpdate r) {
    return service().updateAccount(id, r);
  }

  @PutMapping("/accounts/{id}/roles")
  @com.campus.perks.security.service.AuditedOperation(
      action = "RBAC_GRANTS_REPLACED",
      resourceType = "ACCOUNT",
      resourceArgument = 0)
  @Operation(summary = "rbac:manage 按版本替换同端角色，禁止修改自身授权")
  public Account grants(@PathVariable String id, @Valid @RequestBody Grants r) {
    return service().grants(id, r);
  }
}
