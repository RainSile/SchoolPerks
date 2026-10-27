package com.campus.perks.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** External provider settings; credentials must never appear in diagnostic strings. */
@ConfigurationProperties(prefix = "campus.integrations")
public record IntegrationProperties(Minio minio, Ai ai, Pinecone pinecone) {
  public record Minio(
      boolean enabled,
      String endpoint,
      String publicEndpoint,
      String accessKey,
      String secretKey,
      String bucket,
      String region,
      java.time.Duration connectTimeout,
      java.time.Duration readTimeout,
      java.time.Duration writeTimeout,
      java.time.Duration callTimeout,
      long maxObjectBytes) {
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
