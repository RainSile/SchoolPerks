package com.campus.perks.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "本人文件元数据，不含桶、key 或永久 URL")
public record UploadMetadata(
    String id,
    String fileName,
    String contentType,
    long size,
    String purpose,
    String status,
    Instant createdAt,
    Instant expiresAt,
    Instant completedAt) {}
