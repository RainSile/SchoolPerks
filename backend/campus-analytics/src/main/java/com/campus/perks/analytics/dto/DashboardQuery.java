package com.campus.perks.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Schema(description = "商家经营指标查询")
public class DashboardQuery {
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  @Schema(description = "统计开始日期，含当日；from/to同时省略时采用默认范围")
  private LocalDate from;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  @Schema(description = "统计结束日期，含当日")
  private LocalDate to;

  @Size(max = 64)
  @Schema(description = "配置允许的IANA时区，默认Asia/Shanghai")
  private String zone;

  @Schema(description = "跳过短期统计缓存，权限仍实时校验")
  private boolean refresh;

  @Pattern(regexp = "[1-9][0-9]{0,17}")
  @Schema(description = "仅兼容旧/merchant/dashboard接口时必填；新接口使用路径门店ID")
  private String merchantId;

  public LocalDate getFrom() {
    return from;
  }

  public void setFrom(LocalDate value) {
    from = value;
  }

  public LocalDate getTo() {
    return to;
  }

  public void setTo(LocalDate value) {
    to = value;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String value) {
    zone = value;
  }

  public boolean isRefresh() {
    return refresh;
  }

  public void setRefresh(boolean value) {
    refresh = value;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String value) {
    merchantId = value;
  }
}
