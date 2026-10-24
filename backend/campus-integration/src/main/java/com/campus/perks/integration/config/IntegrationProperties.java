package com.campus.perks.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置契约；当前没有创建客户端的自动配置。 */
@ConfigurationProperties(prefix = "campus.integrations")
public record IntegrationProperties(Minio minio, Ai ai, Pinecone pinecone) {
  public record Minio(
      boolean enabled, String endpoint, String accessKey, String secretKey, String bucket) {
    @Override
    public String toString() {
      return "Minio[REDACTED]";
    }
  }

  public record Ai(
      boolean enabled,
      String baseUrl,
      String apiKey,
      String chatModel,
      String embeddingModel,
      int embeddingDimensions,
      String embeddingBaseUrl,
      String embeddingApiKey,
      java.time.Duration timeout,
      int maxTokens,
      int maxEmbeddingChars) {
    @Override
    public String toString() {
      return "Ai[REDACTED]";
    }
  }

  public record Pinecone(
      boolean enabled,
      String apiKey,
      String indexHost,
      String namespace,
      java.time.Duration timeout,
      String vectorField) {
    @Override
    public String toString() {
      return "Pinecone[REDACTED]";
    }
  }

  @Override
  public String toString() {
    return "IntegrationProperties[REDACTED]";
  }
}
