package com.campus.perks.integration.serviceImpl;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.ObjectStoragePort;
import io.minio.*;
import io.minio.http.Method;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Private bucket adapter: browser credentials never authorize writes to sealed objects. */
public final class MinioObjectStorage implements ObjectStoragePort {
  private final MinioClient storage;
  private final MinioClient signing;
  private final String bucket;
  private final long maxObjectBytes;

  public MinioObjectStorage(
      MinioClient storage, MinioClient signing, String bucket, long maxObjectBytes) {
    this.storage = storage;
    this.signing = signing;
    this.bucket = bucket;
    this.maxObjectBytes = maxObjectBytes;
  }

  @Override
  public boolean available() {
    try {
      return storage.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  public URI createUploadUrl(String key, String contentType, long size, Duration expiry) {
    if (size < 1 || size > maxObjectBytes || contentType == null || contentType.isBlank())
      throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "上传大小或类型不符合要求");
    try {
      return URI.create(
          signing.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(bucket)
                  .object(key)
                  .expiry(seconds(expiry))
                  .extraHeaders(
                      Map.of("Content-Type", contentType, "Content-Length", Long.toString(size)))
                  .build()));
    } catch (Exception e) {
      throw unavailable();
    }
  }

  @Override
  public StoredObject seal(String source, String target) {
    try {
      var original =
          storage.statObject(StatObjectArgs.builder().bucket(bucket).object(source).build());
      if (original.size() > maxObjectBytes)
        return new StoredObject(original.size(), original.contentType(), new byte[0]);
      storage.copyObject(
          CopyObjectArgs.builder()
              .bucket(bucket)
              .object(target)
              .source(CopySource.builder().bucket(bucket).object(source).build())
              .build());
      var snapshot =
          storage.statObject(StatObjectArgs.builder().bucket(bucket).object(target).build());
      // Read a bounded prefix of the immutable copy, never the mutable staging upload.
      try (var input =
          storage.getObject(
              GetObjectArgs.builder()
                  .bucket(bucket)
                  .object(target)
                  .offset(0L)
                  .length(16L)
                  .build())) {
        return new StoredObject(snapshot.size(), snapshot.contentType(), input.readNBytes(16));
      }
    } catch (Exception e) {
      throw unavailable();
    }
  }

  @Override
  public URI createDownloadUrl(String key, Duration expiry) {
    try {
      return URI.create(
          signing.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(bucket)
                  .object(key)
                  .expiry(seconds(expiry))
                  .extraQueryParams(
                      Map.of(
                          "response-cache-control",
                          "private, no-store",
                          "response-content-disposition",
                          "attachment"))
                  .build()));
    } catch (Exception e) {
      throw unavailable();
    }
  }

  @Override
  public void delete(String key) {
    try {
      storage.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (Exception e) {
      throw unavailable();
    }
  }

  private static int seconds(Duration expiry) {
    long seconds = expiry == null ? 0 : expiry.toSeconds();
    if (seconds < 1 || seconds > Duration.ofDays(7).toSeconds())
      throw new IllegalArgumentException("Presigned URL expiry must be within seven days");
    return Math.toIntExact(seconds);
  }

  private static ApplicationException unavailable() {
    // Provider exceptions may contain signed URLs and credentials; do not attach them.
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "文件存储服务暂不可用");
  }
}
