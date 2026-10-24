package com.campus.perks.file.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.file.dto.*;
import com.campus.perks.file.service.FileService;
import com.campus.perks.file.vo.*;
import com.campus.perks.integration.port.FileAccessPort;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "私有资质文件")
public class FileController {
  private final FileService service;

  public FileController(FileService service) {
    this.service = service;
  }

  @GetMapping("/storage-capabilities")
  @Operation(summary = "查询文件服务可用性与限制")
  public StorageCapabilities capabilities() {
    return service.capabilities();
  }

  @PostMapping("/uploads")
  @Operation(summary = "创建本人私有上传凭据", description = "未配置存储返回 503；客户端不能指定 owner、桶或对象 key。")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<UploadTicket> create(@Valid @RequestBody CreateUploadRequest r) {
    var result = service.create(r);
    return ResponseEntity.created(URI.create("/api/v1/uploads/" + result.id()))
        .cacheControl(CacheControl.noStore())
        .body(result);
  }

  @GetMapping("/uploads")
  @Operation(summary = "分页查询本人文件元数据")
  @SecurityRequirement(name = "bearerAuth")
  public PageResponse<UploadMetadata> list(@Valid @ParameterObject PageQuery query) {
    return service.list(query);
  }

  @GetMapping("/uploads/{id}")
  @Operation(summary = "查询本人文件元数据")
  @SecurityRequirement(name = "bearerAuth")
  public UploadMetadata metadata(@PathVariable String id) {
    return service.metadata(id);
  }

  @PutMapping("/uploads/{id}/completion")
  @Operation(summary = "确认上传并封存私有文件", description = "核对实际大小/MIME/文件头；封存到不可被原上传链接覆盖的新对象。重复确认幂等。")
  @SecurityRequirement(name = "bearerAuth")
  public UploadMetadata complete(@PathVariable String id) {
    return service.complete(id);
  }

  @PostMapping("/uploads/{id}/download-tickets")
  @Operation(summary = "创建本人文件短时下载凭据")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<FileAccessPort.Download> download(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.download(id));
  }
}
