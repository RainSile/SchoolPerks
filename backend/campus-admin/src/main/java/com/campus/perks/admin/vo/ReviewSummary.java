package com.campus.perks.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "平台审核响应契约；具体字段在实现阶段扩充")
public record ReviewSummary(
    @Schema(description = "审核 ID") String id, @Schema(description = "审核状态") String status) {}
