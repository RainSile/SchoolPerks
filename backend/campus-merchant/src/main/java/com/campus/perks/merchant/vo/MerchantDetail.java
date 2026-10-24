package com.campus.perks.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "审核通过门店的公开详情")
public record MerchantDetail(
    String id,
    String name,
    String schoolId,
    String category,
    String address,
    BigDecimal longitude,
    BigDecimal latitude,
    String description,
    String businessHours) {}
