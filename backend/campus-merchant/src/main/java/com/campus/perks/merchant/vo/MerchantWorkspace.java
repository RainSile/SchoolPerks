package com.campus.perks.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "本店工作台，仅门店成员可见；资质另外鉴权")
public record MerchantWorkspace(
    String id,
    String name,
    String schoolId,
    String category,
    String address,
    String status,
    int version,
    String description,
    String businessHours,
    String role) {}
