package com.campus.perks.merchant.service;

import com.campus.perks.common.api.*;
import com.campus.perks.merchant.dto.LocationRequests.*;
import com.campus.perks.merchant.vo.LocationViews.*;

public interface LocationService {
  default com.campus.perks.common.exception.ApplicationException unavailable() {
    return new com.campus.perks.common.exception.ApplicationException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "LOCATION_UNAVAILABLE",
        "附近商家需要启用数据库与认证业务");
  }

  default Location location(String id) {
    throw unavailable();
  }

  default Location save(String id, Input input) {
    throw unavailable();
  }

  default PageResponse<Nearby> nearby(NearbyQuery query) {
    throw unavailable();
  }
}
