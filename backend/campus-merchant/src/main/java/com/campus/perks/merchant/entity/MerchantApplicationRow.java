package com.campus.perks.merchant.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record MerchantApplicationRow(
    String id,
    Long applicantId,
    Long schoolId,
    String schoolName,
    String name,
    String category,
    String address,
    BigDecimal longitude,
    BigDecimal latitude,
    String description,
    String qualificationFileId,
    String status,
    Long merchantId,
    String reviewReason,
    Instant submittedAt,
    Instant reviewedAt) {}
