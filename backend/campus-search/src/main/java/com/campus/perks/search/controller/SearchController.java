package com.campus.perks.search.controller;

import com.campus.perks.common.api.PageResponse;
import com.campus.perks.search.dto.SearchQuery;
import com.campus.perks.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "全文检索")
public class SearchController {
  private final SearchService service;

  public SearchController(SearchService service) {
    this.service = service;
  }

  @GetMapping("/search-results")
  @Operation(
      summary = "公开商家/优惠/套餐/帖子搜索；明确返回MYSQL或ELASTICSEARCH后端",
      description = "固定可搜索字段、关键词最长100字符、受限结果窗口；索引候选在主库重新验证可见性。高亮为纯文本片段，禁止HTML注入。")
  public com.campus.perks.search.vo.SearchViews.Results query(
      @Valid @ParameterObject SearchQuery query) {
    return service.query(query);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/admin/search-index-rebuilds")
  @Operation(summary = "search:manage 创建持久索引重建任务，不自动创建远端索引")
  public org.springframework.http.ResponseEntity<com.campus.perks.search.vo.SearchViews.Rebuild>
      rebuild() {
    var r = service.createRebuild();
    return org.springframework.http.ResponseEntity.created(
            java.net.URI.create("/api/v1/admin/search-index-rebuilds/" + r.id()))
        .body(r);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/admin/search-index-rebuilds")
  @Operation(summary = "search:manage 查询索引重建任务分页")
  public PageResponse<com.campus.perks.search.vo.SearchViews.Rebuild> rebuilds(
      @Valid @ParameterObject com.campus.perks.common.api.PageQuery q) {
    return service.rebuilds(q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/admin/search-index-rebuilds/{id}")
  @Operation(summary = "search:manage 查询重建进度、游标与错误状态")
  public com.campus.perks.search.vo.SearchViews.Rebuild rebuild(@PathVariable String id) {
    return service.rebuild(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/admin/search-index-rebuilds/{id}/retries")
  @Operation(summary = "search:manage 重试失败的索引重建，保留已处理游标")
  public org.springframework.http.ResponseEntity<com.campus.perks.search.vo.SearchViews.Rebuild>
      retry(@PathVariable String id) {
    return org.springframework.http.ResponseEntity.accepted()
        .location(java.net.URI.create("/api/v1/admin/search-index-rebuilds/" + id))
        .body(service.retry(id));
  }
}
