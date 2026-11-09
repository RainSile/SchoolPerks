package com.campus.perks.user.vo;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description="头像引用；imageUrl为稳定业务入口，不包含存储凭据")
public record AvatarView(String fileId, String defaultKey, String imageUrl) {}
