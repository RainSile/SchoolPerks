package com.campus.perks.merchant.service;

import com.campus.perks.common.api.PageResponse;
import com.campus.perks.merchant.dto.MerchantQuery;
import com.campus.perks.merchant.vo.MerchantSummary;

public interface MerchantService {
  com.campus.perks.merchant.vo.MerchantDetail detail(String id);

  PageResponse<MerchantSummary> list(MerchantQuery query);
}
