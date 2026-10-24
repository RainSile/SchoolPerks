package com.campus.perks.admin.service;

import com.campus.perks.common.api.*;
import com.campus.perks.integration.contract.ReviewRecord;

public interface ReviewService {
  PageResponse<ReviewRecord> query(PageQuery query);

  ReviewRecord get(String id);
}
