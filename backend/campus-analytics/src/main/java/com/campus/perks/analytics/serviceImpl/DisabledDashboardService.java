package com.campus.perks.analytics.serviceImpl;

import com.campus.perks.analytics.dto.DashboardQuery;
import com.campus.perks.analytics.service.DashboardService;
import com.campus.perks.analytics.vo.DashboardSummary;
import com.campus.perks.common.exception.ApplicationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledDashboardService implements DashboardService {
  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "ANALYTICS_UNAVAILABLE", "统计服务需要数据库和认证配置");
  }

  public DashboardSummary query(DashboardQuery q) {
    throw unavailable();
  }

  public DashboardSummary merchant(String id, DashboardQuery q) {
    throw unavailable();
  }

  public DashboardSummary platform(DashboardQuery q) {
    throw unavailable();
  }
}
