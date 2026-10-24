package com.campus.perks.integration.config;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.ObjectStoragePort;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;

@Configuration
public class ObjectStorageConfiguration {
  @Bean
  @ConditionalOnMissingBean(ObjectStoragePort.class)
  ObjectStoragePort unavailableObjectStorage() {
    return new ObjectStoragePort() {
      private ApplicationException unavailable() {
        return new ApplicationException(
            HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "文件存储服务尚未配置");
      }

      public boolean available() {
        return false;
      }

      public URI createUploadUrl(String key, String type, Duration expiry) {
        throw unavailable();
      }

      public StoredObject seal(String source, String target) {
        throw unavailable();
      }

      public URI createDownloadUrl(String key, Duration expiry) {
        throw unavailable();
      }

      public void delete(String key) {
        throw unavailable();
      }
    };
  }
}
