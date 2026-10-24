package com.campus.perks.user.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.CampusReviewPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledCampusReview implements CampusReviewPort {
  private ApplicationException unavailable() {
    return new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "认证服务尚未启用");
  }

  public PageResponse<Entry> applications(int page, int size, String status) {
    throw unavailable();
  }

  public Identity identity(String id) {
    throw unavailable();
  }

  public ReviewRecord decide(String id, String decision, String reason) {
    throw unavailable();
  }

  public ReviewRecord review(String id) {
    throw unavailable();
  }
}
