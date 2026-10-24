package com.campus.perks.community.dto;

import com.campus.perks.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "种草社区查询")
public class PostQuery extends PageQuery {
  @jakarta.validation.constraints.Pattern(regexp = "[1-9][0-9]{0,17}")
  private String schoolId;

  public String getSchoolId() {
    return schoolId;
  }

  public void setSchoolId(String value) {
    schoolId = value;
  }

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
