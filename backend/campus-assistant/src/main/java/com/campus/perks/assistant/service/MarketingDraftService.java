package com.campus.perks.assistant.service;

import com.campus.perks.assistant.dto.*;
import com.campus.perks.assistant.vo.MarketingViews.*;
import com.campus.perks.common.api.PageResponse;

public interface MarketingDraftService {
  Capabilities capabilities(String merchant);

  Result generate(String merchant, String key, CreateMarketingDraftRequest request);

  Result retry(String merchant, String id, RetryMarketingDraftRequest request);

  PageResponse<Draft> list(String merchant, int page, int size);

  Draft get(String merchant, String id);

  Draft update(String merchant, String id, UpdateMarketingDraftRequest request);

  Draft archive(String merchant, String id, ArchiveMarketingDraftRequest request);
}
