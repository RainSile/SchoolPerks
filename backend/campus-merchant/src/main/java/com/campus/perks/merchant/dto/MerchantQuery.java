package com.campus.perks.merchant.dto;

import com.campus.perks.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "商家查询；仅展示审核通过的商家")
public class MerchantQuery extends PageQuery {
  @Size(max = 100)
  @Schema(description = "商家名关键词")
  private String keyword;

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }
}
