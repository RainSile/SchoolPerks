package com.campus.perks.integration.config;

import com.campus.perks.integration.port.ObjectStoragePort;
import com.campus.perks.integration.serviceImpl.MinioObjectStorage;
import io.minio.MinioClient;
import java.net.URI;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "campus.integrations.minio.enabled", havingValue = "true")
public class MinioStorageConfiguration {
  @Bean
  ObjectStoragePort minioObjectStorage(IntegrationProperties properties) {
    var p = properties.minio();
    if (p == null
        || blank(p.accessKey())
        || blank(p.secretKey())
        || blank(p.bucket())
        || blank(p.region())
        || p.maxObjectBytes() < 1) {
      throw new IllegalStateException(
          "MinIO requires endpoint, private credentials, bucket, region and size limit");
    }
    validateEndpoint(p.endpoint());
    String publicEndpoint = blank(p.publicEndpoint()) ? p.endpoint() : p.publicEndpoint();
    validateEndpoint(publicEndpoint);
    var http =
        new OkHttpClient.Builder()
            .connectTimeout(timeout(p.connectTimeout()))
            .readTimeout(timeout(p.readTimeout()))
            .writeTimeout(timeout(p.writeTimeout()))
            .callTimeout(timeout(p.callTimeout()))
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
    var internal =
        MinioClient.builder()
            .endpoint(p.endpoint())
            .region(p.region())
            .credentials(p.accessKey(), p.secretKey())
            .httpClient(http)
            .build();
    var signing =
        MinioClient.builder()
            .endpoint(publicEndpoint)
            .region(p.region())
            .credentials(p.accessKey(), p.secretKey())
            .httpClient(http)
            .build();
    return new MinioObjectStorage(internal, signing, p.bucket(), p.maxObjectBytes());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static Duration timeout(Duration value) {
    if (value == null
        || value.isNegative()
        || value.isZero()
        || value.compareTo(Duration.ofMinutes(1)) > 0)
      throw new IllegalStateException("MinIO timeouts must be positive and at most one minute");
    return value;
  }

  private static void validateEndpoint(String endpoint) {
    try {
      URI uri = URI.create(endpoint);
      if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null
          || uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
        throw new IllegalArgumentException();
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "MinIO endpoint must be an HTTP(S) origin without credentials or a path");
    }
  }
}
