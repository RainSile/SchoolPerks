package com.campus.perks.analytics.service;

import com.campus.perks.analytics.dto.DashboardQuery;
import com.campus.perks.analytics.vo.DashboardSummary;

public interface DashboardService {
  DashboardSummary query(DashboardQuery query);

  DashboardSummary merchant(String merchantId, DashboardQuery query);

  DashboardSummary platform(DashboardQuery query);
}
