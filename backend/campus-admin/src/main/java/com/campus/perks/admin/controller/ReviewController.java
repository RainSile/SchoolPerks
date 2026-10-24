package com.campus.perks.admin.controller;

import com.campus.perks.admin.service.ReviewService;
import com.campus.perks.common.api.*;
import com.campus.perks.integration.contract.ReviewRecord;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/merchant-reviews")
@Tag(name = "商家审核历史")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('merchant:review')")
public class ReviewController {
  private final ReviewService service;

  public ReviewController(ReviewService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "分页查询商家审核历史")
  public PageResponse<ReviewRecord> query(@Valid @ParameterObject PageQuery q) {
    return service.query(q);
  }

  @GetMapping("/{id}")
  @Operation(summary = "查询不可覆写的商家审核记录")
  public ReviewRecord get(@PathVariable String id) {
    return service.get(id);
  }
}
