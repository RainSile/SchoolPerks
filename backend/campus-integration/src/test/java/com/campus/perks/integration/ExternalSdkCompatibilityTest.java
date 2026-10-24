package com.campus.perks.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 仅验证 SDK 在 Java 17 下类加载和无网络构建；不冒充远程服务集成测试。 */
@EnabledIfSystemProperty(named = "campus.sdkVerification", matches = "true")
class ExternalSdkCompatibilityTest {
  @Test
  void vendorSdkClassesLoadTogetherAndMinioBuildsWithoutNetwork() throws Exception {
    assertThat(Class.forName("dev.langchain4j.model.chat.ChatModel")).isNotNull();
    assertThat(Class.forName("dev.langchain4j.model.openai.OpenAiChatModel")).isNotNull();
    assertThat(Class.forName("dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore"))
        .isNotNull();
    assertThat(
            Class.forName("okhttp3.logging.HttpLoggingInterceptor").getConstructor().newInstance())
        .isNotNull();
    assertThat(Class.forName("kotlin.KotlinVersion").getField("CURRENT").get(null).toString())
        .isEqualTo("2.2.0");
    Class<?> client = Class.forName("io.minio.MinioClient");
    Object builder = client.getMethod("builder").invoke(null);
    Method endpoint = builder.getClass().getMethod("endpoint", String.class);
    endpoint.invoke(builder, "http://127.0.0.1:1");
    assertThat(builder.getClass().getMethod("build").invoke(builder)).isNotNull();
  }
}
