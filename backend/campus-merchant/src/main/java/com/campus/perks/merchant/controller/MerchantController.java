package com.campus.perks.merchant.controller;

import com.campus.perks.common.api.PageResponse;
import com.campus.perks.merchant.dto.MerchantQuery;
import com.campus.perks.merchant.service.MerchantService;
import com.campus.perks.merchant.vo.MerchantSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "周边商家")
public class MerchantController {
  private final MerchantService service;

  public MerchantController(MerchantService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  @Operation(summary = "查询已审核商家的公开详情")
  public com.campus.perks.merchant.vo.MerchantDetail detail(@PathVariable String id) {
    return service.detail(id);
  }

  @GetMapping
  @Operation(summary = "分页查询已审核商家", description = "基础数据库读取示例；距离排序留待 LBS 阶段。")
  @ApiResponse(responseCode = "200", description = "查询成功")
  @ApiResponse(responseCode = "400", description = "分页参数非法")
  @ApiResponse(responseCode = "501", description = "数据库未启用")
  public PageResponse<MerchantSummary> list(@Valid @ParameterObject MerchantQuery query) {
    return service.list(query);
  }
}
