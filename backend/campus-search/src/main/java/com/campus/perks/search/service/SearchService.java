package com.campus.perks.search.service;

import com.campus.perks.common.api.PageResponse;
import com.campus.perks.search.dto.SearchQuery;

public interface SearchService {
  default com.campus.perks.search.vo.SearchViews.Results query(SearchQuery query) {
    throw unavailable();
  }

  default com.campus.perks.search.vo.SearchViews.Rebuild createRebuild() {
    throw unavailable();
  }

  default com.campus.perks.search.vo.SearchViews.Rebuild rebuild(String id) {
    throw unavailable();
  }

  default PageResponse<com.campus.perks.search.vo.SearchViews.Rebuild> rebuilds(
      com.campus.perks.common.api.PageQuery q) {
    throw unavailable();
  }

  default com.campus.perks.search.vo.SearchViews.Rebuild retry(String id) {
    throw unavailable();
  }

  default com.campus.perks.common.exception.ApplicationException unavailable() {
    return new com.campus.perks.common.exception.ApplicationException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "SEARCH_UNAVAILABLE",
        "搜索需要启用数据库与认证业务");
  }
}
