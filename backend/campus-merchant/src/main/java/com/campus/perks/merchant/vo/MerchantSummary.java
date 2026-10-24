package com.campus.perks.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "商家公开信息，不包含资质及个人资料")
public record MerchantSummary(
    @Schema(description = "商家 ID，以字符串传输避免 JS 精度损失") String id,
    @Schema(description = "商家名称") String name,
    @Schema(description = "分类编码") String category,
    @Schema(description = "门店地址") String address) {}
