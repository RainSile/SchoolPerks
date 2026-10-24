package com.campus.perks.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "暂停或恢复已审核门店")
public record StatusChangeRequest(
    @NotBlank @Pattern(regexp = "APPROVED|SUSPENDED") String status,
    @Min(0) @NotNull Integer version,
    @NotBlank @Size(max = 255) String reason) {}
