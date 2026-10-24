package com.campus.perks.integration.port;

import com.campus.perks.common.api.PageResponse;
import com.campus.perks.integration.contract.ReviewRecord;
import java.math.BigDecimal;
import java.time.Instant;

public interface MerchantReviewPort {
  PageResponse<Application> applications(int page, int size, String status);

  Application inspect(String id);

  ReviewRecord decide(String id, String decision, String reason);

  PageResponse<ReviewRecord> history(int page, int size);

  ReviewRecord review(String id);

  FileAccessPort.Download reviewQualification(String id);

  MerchantState merchant(String id);

  record MerchantState(String id, String name, String status, int version) {}

  StatusChange statusChange(String merchantId, String changeId);

  StatusChange changeStatus(String id, String status, int version, String reason);

  record Application(
      String id,
      String applicantId,
      String schoolId,
      String schoolName,
      String name,
      String category,
      String address,
      BigDecimal longitude,
      BigDecimal latitude,
      String description,
      String qualificationFileId,
      String status,
      String merchantId,
      String reviewReason,
      Instant submittedAt,
      Instant reviewedAt) {}

  record StatusChange(
      String id,
      String merchantId,
      String previousStatus,
      String status,
      int version,
      String actorId,
      String reason,
      Instant createdAt) {}
}
