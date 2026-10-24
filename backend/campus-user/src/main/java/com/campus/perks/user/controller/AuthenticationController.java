package com.campus.perks.user.controller;

import com.campus.perks.user.dto.*;
import com.campus.perks.user.service.AuthenticationService;
import com.campus.perks.user.vo.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "认证与会话")
public class AuthenticationController {
  private final AuthenticationService service;

  public AuthenticationController(AuthenticationService service) {
    this.service = service;
  }

  @GetMapping("/authentication-capabilities")
  @Operation(summary = "查询认证及短信可用性")
  public AuthenticationCapabilities capabilities() {
    return service.capabilities();
  }

  @PostMapping("/verification-challenges")
  @Operation(summary = "创建短信验证码挑战", description = "用途为登录、注册或重置密码；短信未配置返回 503；不返回验证码。")
  @ApiResponse(responseCode = "201", description = "短信发送已受理，挑战已创建")
  @ApiResponse(responseCode = "429", description = "发送频率受限")
  @ApiResponse(responseCode = "503", description = "短信服务不可用")
  public ResponseEntity<ChallengeView> challenge(
      @Valid @RequestBody CreateChallengeRequest request, HttpServletRequest http) {
    var view = service.createChallenge(request, http.getRemoteAddr());
    return ResponseEntity.created(URI.create("/api/v1/verification-challenges/" + view.id()))
        .cacheControl(CacheControl.noStore())
        .body(view);
  }

  @GetMapping("/verification-challenges/{id}")
  @Operation(summary = "查询验证码挑战状态", description = "仅返回随机 ID、状态和过期时间，不返回手机号或验证码。")
  public ChallengeView challenge(@PathVariable String id) {
    return service.challenge(id);
  }

  @PostMapping("/sessions")
  @Operation(summary = "手机号验证码登录", description = "首次成功登录自动创建普通用户；验证码仅消费一次。")
  @ApiResponse(responseCode = "201", description = "会话已创建，令牌仅出现在此敏感响应中")
  @ApiResponse(responseCode = "401", description = "验证码或账号无效")
  public ResponseEntity<SessionTokens> login(@Valid @RequestBody CreateSessionRequest request) {
    return tokens(service.createSession(request));
  }

  @PostMapping("/session-refreshes")
  @Operation(summary = "创建一次会话刷新", description = "轮换刷新令牌；旧令牌重复使用会撤销整个会话。")
  @ApiResponse(responseCode = "201", description = "刷新成功，返回轮换后的令牌")
  @ApiResponse(responseCode = "401", description = "会话无效或发现刷新令牌重放")
  public ResponseEntity<SessionTokens> refresh(@Valid @RequestBody RefreshSessionRequest request) {
    return tokens(service.refresh(request));
  }

  private ResponseEntity<SessionTokens> tokens(SessionTokens tokens) {
    return ResponseEntity.created(URI.create("/api/v1/sessions/current"))
        .cacheControl(CacheControl.noStore())
        .body(tokens);
  }

  @PostMapping("/password-sessions")
  @Operation(summary = "手机号密码登录", description = "返回现有账号的会话，入口角色以服务端账号为准。")
  public ResponseEntity<SessionTokens> passwordLogin(
      @Valid @RequestBody PasswordLoginRequest request, HttpServletRequest http) {
    return tokens(service.passwordLogin(request, http.getRemoteAddr()));
  }

  @PostMapping("/registrations")
  @Operation(summary = "注册普通用户并创建会话", description = "消费 REGISTER 短信挑战；手机号不可重复；不接受客户端角色。")
  public ResponseEntity<SessionTokens> register(@Valid @RequestBody RegistrationRequest request) {
    return tokens(service.register(request));
  }

  @PostMapping("/password-resets")
  @Operation(summary = "通过短信验证码重置密码", description = "消费 RESET_PASSWORD 挑战并撤销该账号全部会话。")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
    service.resetPassword(request);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @PutMapping("/users/me/password")
  @Operation(summary = "修改自己的密码", description = "验证当前密码并撤销全部会话，成功后需重新登录。首次设置密码可通过短信找回流程完成。")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PasswordChangeRequest request,
      HttpServletRequest http) {
    service.changePassword(jwt.getSubject(), request, http.getRemoteAddr());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @DeleteMapping("/sessions/current")
  @Operation(summary = "撤销当前会话", description = "立即阻止该会话的 access/refresh 令牌继续使用。")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(responseCode = "204", description = "会话已撤销")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
    service.logout(jwt.getSubject(), jwt.getClaimAsString("sid"));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/sessions/current")
  @Operation(summary = "查询当前登录用户", description = "不返回任何令牌或会话凭据。")
  @SecurityRequirement(name = "bearerAuth")
  public java.util.Map<String, String> current(@AuthenticationPrincipal Jwt jwt) {
    return java.util.Map.of("userId", jwt.getSubject());
  }

  @GetMapping("/schools")
  @Operation(summary = "查询校园认证可选学校", description = "最多返回 1000 所；无主数据时为空列表。")
  public List<SchoolView> schools() {
    return service.schools();
  }
}
