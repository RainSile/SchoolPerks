package com.campus.perks.file.service;

import com.campus.perks.common.api.*;
import com.campus.perks.file.dto.CreateUploadRequest;
import com.campus.perks.file.vo.*;
import com.campus.perks.integration.port.FileAccessPort;

public interface FileService {
  StorageCapabilities capabilities();

  UploadTicket create(CreateUploadRequest request);

  UploadMetadata complete(String id);

  UploadMetadata metadata(String id);

  PageResponse<UploadMetadata> list(PageQuery query);

  FileAccessPort.Download download(String id);
}
