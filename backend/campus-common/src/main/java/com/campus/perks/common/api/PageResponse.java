package com.campus.perks.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "分页资源")
public record PageResponse<T>(
    @Schema(description = "本页数据") List<T> items,
    @Schema(description = "总条数") long total,
    @Schema(description = "页码") long page,
    @Schema(description = "每页条数") long size) {}
