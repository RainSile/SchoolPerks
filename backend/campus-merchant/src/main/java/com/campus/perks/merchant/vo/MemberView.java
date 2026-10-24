package com.campus.perks.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "本店成员，不返回手机号或其他个人资料")
public record MemberView(
    String userId, String nickname, String role, String userStatus, Instant createdAt) {}
