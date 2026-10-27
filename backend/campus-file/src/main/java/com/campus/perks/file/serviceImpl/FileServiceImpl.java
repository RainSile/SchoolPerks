package com.campus.perks.file.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.file.config.FileProperties;
import com.campus.perks.file.dto.CreateUploadRequest;
import com.campus.perks.file.entity.FileObject;
import com.campus.perks.file.mapper.FileMapper;
import com.campus.perks.file.service.FileService;
import com.campus.perks.file.vo.*;
import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.CurrentActor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class FileServiceImpl implements FileService, FileAccessPort {
  private final FileMapper files;
  private final ObjectStoragePort storage;
  private final FileProperties p;
  private final AccountDirectoryPort accounts;
  private final AuditPort audit;
  private final Clock clock;

  public FileServiceImpl(
      FileMapper files,
      ObjectStoragePort storage,
      FileProperties p,
      AccountDirectoryPort accounts,
      AuditPort audit,
      Clock clock) {
    this.files = files;
    this.storage = storage;
    this.p = p;
    this.accounts = accounts;
    this.audit = audit;
    this.clock = clock;
  }

  public StorageCapabilities capabilities() {
    return new StorageCapabilities(
        true,
        storage.available(),
        p.maxBytes(),
        p.allowedTypes(),
        p.uploadTtl().toSeconds(),
        p.downloadTtl().toSeconds());
  }

  private ApplicationException error(HttpStatus status, String code, String detail) {
    return new ApplicationException(status, code, detail);
  }

  private void available() {
    if (!storage.available())
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "文件存储服务尚未配置");
  }

  private URI url(URI value) {
    if (value == null
        || !("https".equalsIgnoreCase(value.getScheme())
            || "http".equalsIgnoreCase(value.getScheme()))
        || value.getHost() == null
        || value.getUserInfo() != null)
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_INVALID_RESPONSE", "文件服务响应无效");
    return value;
  }

  @Transactional
  public UploadTicket create(CreateUploadRequest r) {
    String owner = CurrentActor.id();
    available();
    if (r.size() > p.maxBytes()
        || !p.allowedTypes().contains(r.contentType())
        || "COMMUNITY_IMAGE".equals(r.purpose())
            && !java.util.Set.of("image/png", "image/jpeg").contains(r.contentType())
        || r.fileName().contains("/")
        || r.fileName().contains("\\")
        || r.fileName().codePoints().anyMatch(Character::isISOControl))
      throw error(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "文件类型、名称或大小不符合要求");
    accounts.lockActiveUser(owner);
    var now = clock.instant();
    if (files.pending(Long.parseLong(owner), now) >= p.maxPendingPerUser())
      throw error(HttpStatus.TOO_MANY_REQUESTS, "UPLOAD_LIMIT", "待完成上传过多，请稍后重试");
    String id = UUID.randomUUID().toString();
    String staging = p.objectPrefix() + "/pending/" + id;
    String immutable = p.objectPrefix() + "/sealed/" + UUID.randomUUID();
    URI upload;
    try {
      upload = url(storage.createUploadUrl(staging, r.contentType(), r.size(), p.uploadTtl()));
    } catch (ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "无法创建上传凭据");
    }
    files.insert(
        new FileObject(
            id,
            Long.parseLong(owner),
            r.purpose(),
            r.fileName(),
            r.contentType(),
            r.size(),
            staging,
            immutable,
            "PENDING",
            now,
            now.plus(p.uploadTtl()),
            null));
    audit.record(owner, "FILE_UPLOAD_REQUESTED", "FILE", id, Map.of("purpose", r.purpose()));
    return new UploadTicket(id, upload, r.contentType(), now.plus(p.uploadTtl()));
  }

  private FileObject own(String id, String owner, boolean lock) {
    var file = lock ? files.lock(id) : files.find(id);
    if (file == null || !file.ownerId().toString().equals(owner))
      throw error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在");
    return file;
  }

  @Transactional
  public UploadMetadata complete(String id) {
    String owner = CurrentActor.id();
    accounts.lockActiveUser(owner);
    var file = own(id, owner, true);
    if ("READY".equals(file.status())) return view(file);
    if (!file.expiresAt().isAfter(clock.instant()))
      throw error(HttpStatus.GONE, "UPLOAD_EXPIRED", "上传凭据已过期，请重新申请");
    available();
    ObjectStoragePort.StoredObject object;
    try {
      object = storage.seal(file.stagingKey(), file.immutableKey());
    } catch (RuntimeException e) {
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "无法确认上传，请稍后重试");
    }
    if (object == null
        || object.size() != file.declaredSize()
        || object.size() > p.maxBytes()
        || !file.contentType().equals(object.contentType())
        || !signature(object.contentType(), object.prefix())) {
      try {
        storage.delete(file.immutableKey());
      } catch (RuntimeException ignored) {
        // A failed cleanup must never turn an unverified object into an accessible READY file.
      }
      throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STORED_FILE", "实际文件大小、类型或内容签名不匹配");
    }
    files.complete(id, clock.instant());
    audit.record(owner, "FILE_UPLOAD_COMPLETED", "FILE", id, Map.of("purpose", file.purpose()));
    return view(files.find(id));
  }

  private boolean signature(String type, byte[] bytes) {
    if (bytes == null) return false;
    byte[] expected =
        switch (type) {
          case "application/pdf" -> "%PDF-".getBytes(StandardCharsets.US_ASCII);
          case "image/png" -> new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
          case "image/jpeg" -> new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
          default -> new byte[0];
        };
    return expected.length > 0
        && bytes.length >= expected.length
        && Arrays.equals(expected, Arrays.copyOf(bytes, expected.length));
  }

  public UploadMetadata metadata(String id) {
    return view(own(id, CurrentActor.id(), false));
  }

  public PageResponse<UploadMetadata> list(PageQuery q) {
    long owner = Long.parseLong(CurrentActor.id());
    return new PageResponse<>(
        files.list(owner, q.getSize(), (long) (q.getPage() - 1) * q.getSize()).stream()
            .map(this::view)
            .toList(),
        files.count(owner),
        q.getPage(),
        q.getSize());
  }

  public void requireReadyOwned(String id, String owner, String purpose) {
    var file = own(id, owner, false);
    if (!"READY".equals(file.status()) || !file.purpose().equals(purpose))
      throw error(HttpStatus.CONFLICT, "FILE_NOT_READY", "资质文件尚未完成上传确认");
  }

  public Download signOwnedDownload(String id, String owner) {
    var file = own(id, owner, false);
    requireReadyOwned(id, owner, file.purpose());
    available();
    try {
      return new Download(
          url(storage.createDownloadUrl(file.immutableKey(), p.downloadTtl())),
          clock.instant().plus(p.downloadTtl()));
    } catch (ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "无法创建私有下载凭据");
    }
  }

  @Transactional
  public Download download(String id) {
    String actor = CurrentActor.id();
    var link = signOwnedDownload(id, actor);
    audit.record(actor, "PRIVATE_FILE_DOWNLOAD_ISSUED", "FILE", id, Map.of());
    return link;
  }

  private UploadMetadata view(FileObject f) {
    return new UploadMetadata(
        f.id(),
        f.originalName(),
        f.contentType(),
        f.declaredSize(),
        f.purpose(),
        f.status(),
        f.createdAt(),
        f.expiresAt(),
        f.completedAt());
  }
}
