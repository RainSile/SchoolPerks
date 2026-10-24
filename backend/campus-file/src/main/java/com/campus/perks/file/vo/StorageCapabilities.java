package com.campus.perks.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "文件服务可用性和上传限制")
public record StorageCapabilities(
    boolean enabled,
    boolean available,
    long maxBytes,
    Set<String> allowedTypes,
    long uploadTtlSeconds,
    long downloadTtlSeconds) {}
