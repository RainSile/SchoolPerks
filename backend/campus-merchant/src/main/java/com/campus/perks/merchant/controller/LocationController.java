package com.campus.perks.merchant.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.merchant.dto.LocationRequests.*;
import com.campus.perks.merchant.service.LocationService;
import com.campus.perks.merchant.vo.LocationViews.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "位置与附近商家")
@RequestMapping("/api/v1")
public class LocationController {
  private final LocationService service;

  public LocationController(LocationService service) {
    this.service = service;
  }

  @GetMapping("/nearby-merchants")
  @Operation(summary = "按WGS84球面直线距离排序附近已审核商家；不保存用户位置")
  public ResponseEntity<PageResponse<Nearby>> nearby(@Valid @ParameterObject NearbyQuery q) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.nearby(q));
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/merchants/{id}/location")
  @Operation(summary = "本店成员读取明确坐标系的位置和版本")
  public Location location(@PathVariable String id) {
    return service.location(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PutMapping("/merchants/{id}/location")
  @Operation(summary = "本店有发布资格OWNER/MANAGER设置WGS84位置；未知旧坐标不自动沿用")
  public Location save(@PathVariable String id, @Valid @RequestBody Input input) {
    return service.save(id, input);
  }
}
