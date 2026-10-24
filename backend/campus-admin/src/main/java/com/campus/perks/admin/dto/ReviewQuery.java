package com.campus.perks.admin.dto;

import com.campus.perks.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "平台审核查询")
public class ReviewQuery extends PageQuery {
  @jakarta.validation.constraints.Size(max = 100)
  @Schema(description = "可选关键词，最多 100 字")
  private String keyword;

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }
}
