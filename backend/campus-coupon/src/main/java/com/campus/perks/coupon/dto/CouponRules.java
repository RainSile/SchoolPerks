package com.campus.perks.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Schema(
    description = "规则：threshold为最低消费，AMOUNT_OFF指定amountOff；DISCOUNT指定支付比例discountRate，例如0.80为八折")
public record CouponRules(
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal threshold,
    @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amountOff,
    @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "1", inclusive = false)
        @Digits(integer = 1, fraction = 4)
        BigDecimal discountRate) {}
