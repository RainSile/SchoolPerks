package com.campus.perks.integration.port;

import com.campus.perks.common.api.PageResponse;
import com.campus.perks.integration.contract.ReviewRecord;
import java.time.Instant;

public interface CampusReviewPort {
  PageResponse<Entry> applications(int page, int size, String status);

  Identity identity(String id);

  ReviewRecord decide(String id, String decision, String reason);

  ReviewRecord review(String id);

  record Entry(
      String id,
      String applicantId,
      String schoolId,
      String schoolName,
      String status,
      Instant submittedAt) {}

  record Identity(String realName, String studentNumber) {
    @Override
    public String toString() {
      return "Identity[REDACTED]";
    }
  }
}
