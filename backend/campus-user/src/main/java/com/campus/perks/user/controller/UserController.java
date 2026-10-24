package com.campus.perks.user.controller;

import com.campus.perks.user.dto.*;
import com.campus.perks.user.service.UserService;
import com.campus.perks.user.vo.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "用户与校园认证")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "查询本人资料和当前权限")
  public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
    return service.me(jwt.getSubject());
  }

  @PatchMapping
  @Operation(summary = "修改本人昵称", description = "不接受 userId、角色、手机号、学校及权限等字段。")
  public UserProfile update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    return service.update(jwt.getSubject(), request);
  }

  @PostMapping("/campus-verifications")
  @Operation(summary = "提交本人校园认证申请", description = "身份信息加密存储；提交后等待审核，不自动赋予校园身份。")
  @ApiResponse(responseCode = "201", description = "申请已创建")
  @ApiResponse(responseCode = "409", description = "已有待审核或通过的申请")
  public ResponseEntity<CampusVerificationView> apply(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateCampusVerificationRequest request) {
    var view = service.apply(jwt.getSubject(), request);
    return ResponseEntity.created(URI.create("/api/v1/users/me/campus-verifications/" + view.id()))
        .body(view);
  }

  @GetMapping("/campus-verifications")
  @Operation(summary = "查询本人最近 100 条校园认证记录")
  public List<CampusVerificationView> list(@AuthenticationPrincipal Jwt jwt) {
    return service.verifications(jwt.getSubject());
  }

  @GetMapping("/campus-verifications/{id}")
  @Operation(summary = "查询本人单条校园认证申请", description = "其他用户的申请按不存在处理。")
  public CampusVerificationView verification(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return service.verification(jwt.getSubject(), id);
  }
}
