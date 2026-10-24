package com.campus.perks.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "分页查询，页码从 1 开始")
public class PageQuery {
  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  @Min(1)
  @Schema(description = "页码", defaultValue = "1")
  private int page = DEFAULT_PAGE;

  @Min(1)
  @Max(MAX_SIZE)
  @Schema(description = "每页条数，最多 100", defaultValue = "20")
  private int size = DEFAULT_SIZE;

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }
}
