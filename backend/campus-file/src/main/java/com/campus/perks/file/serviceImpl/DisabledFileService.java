package com.campus.perks.file.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.file.config.FileProperties;
import com.campus.perks.file.dto.CreateUploadRequest;
import com.campus.perks.file.service.FileService;
import com.campus.perks.file.vo.*;
import com.campus.perks.integration.port.FileAccessPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledFileService implements FileService, FileAccessPort {
  private final FileProperties p;

  public DisabledFileService(FileProperties p) {
    this.p = p;
  }

  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "FILE_SERVICE_UNAVAILABLE", "文件服务尚未启用");
  }

  public StorageCapabilities capabilities() {
    return new StorageCapabilities(
        false,
        false,
        p.maxBytes(),
        p.allowedTypes(),
        p.uploadTtl().toSeconds(),
        p.downloadTtl().toSeconds());
  }

  public UploadTicket create(CreateUploadRequest r) {
    throw unavailable();
  }

  public UploadMetadata complete(String id) {
    throw unavailable();
  }

  public UploadMetadata metadata(String id) {
    throw unavailable();
  }

  public PageResponse<UploadMetadata> list(PageQuery query) {
    throw unavailable();
  }

  public Download download(String id) {
    throw unavailable();
  }

  public void requireReadyOwned(String id, String owner, String purpose) {
    throw unavailable();
  }

  public Download signOwnedDownload(String id, String owner) {
    throw unavailable();
  }
}
