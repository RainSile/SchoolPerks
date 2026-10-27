package com.campus.perks.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.campus.perks.integration.serviceImpl.MinioObjectStorage;
import io.minio.*;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MinioObjectStorageTest {
  private MinioClient signer() {
    return MinioClient.builder()
        .endpoint("https://files.example.invalid")
        .region("us-east-1")
        .credentials("unit-test-access", "unit-test-private-secret-not-a-real-key")
        .build();
  }

  @Test
  void presignedUploadBindsTypeAndLengthAndUsesTheBrowserEndpoint() {
    var storage = new MinioObjectStorage(mock(MinioClient.class), signer(), "campus-files", 1024);
    var uri =
        storage.createUploadUrl("private/pending/test", "image/png", 67, Duration.ofMinutes(2));
    assertThat(uri.getHost()).isEqualTo("files.example.invalid");
    assertThat(uri.getUserInfo()).isNull();
    assertThat(uri.getPath()).isEqualTo("/campus-files/private/pending/test");
    assertThat(uri.getQuery())
        .contains("X-Amz-SignedHeaders=content-length;content-type;host", "X-Amz-Expires=120");
  }

  @Test
  void invalidDeclaredSizeIsRejectedBeforeSigning() {
    var storage = new MinioObjectStorage(mock(MinioClient.class), signer(), "campus-files", 1024);
    assertThatThrownBy(
            () -> storage.createUploadUrl("pending/test", "image/png", 1025, Duration.ofMinutes(2)))
        .hasMessageContaining("上传大小或类型不符合要求");
  }

  @Test
  void sealingInspectsTheCopyAndReadsOnlyABoundedPrefix() throws Exception {
    var client = mock(MinioClient.class);
    var original = mock(StatObjectResponse.class);
    var snapshot = mock(StatObjectResponse.class);
    when(original.size()).thenReturn(67L);
    when(snapshot.size()).thenReturn(67L);
    when(snapshot.contentType()).thenReturn("image/png");
    when(client.statObject(any(StatObjectArgs.class))).thenReturn(original, snapshot);
    var response =
        new GetObjectResponse(
            new Headers.Builder().build(),
            "campus-files",
            "us-east-1",
            "sealed/test",
            new ByteArrayInputStream(new byte[67]));
    when(client.getObject(any(GetObjectArgs.class))).thenReturn(response);
    var storage = new MinioObjectStorage(client, signer(), "campus-files", 1024);
    var object = storage.seal("pending/test", "sealed/test");
    assertThat(object.size()).isEqualTo(67);
    assertThat(object.prefix()).hasSize(16);
    var request = ArgumentCaptor.forClass(GetObjectArgs.class);
    verify(client).getObject(request.capture());
    assertThat(request.getValue().object()).isEqualTo("sealed/test");
    assertThat(request.getValue().length()).isEqualTo(16L);
    var copy = ArgumentCaptor.forClass(CopyObjectArgs.class);
    verify(client).copyObject(copy.capture());
    assertThat(copy.getValue().object()).isEqualTo("sealed/test");
    assertThat(copy.getValue().source().object()).isEqualTo("pending/test");
  }

  @Test
  void unavailableProviderDoesNotLeakItsException() throws Exception {
    var client = mock(MinioClient.class);
    when(client.bucketExists(any(BucketExistsArgs.class)))
        .thenThrow(new java.io.IOException("sensitive-provider-detail"));
    var storage = new MinioObjectStorage(client, signer(), "campus-files", 1024);
    assertThat(storage.available()).isFalse();
    doThrow(new java.io.IOException("sensitive-provider-detail"))
        .when(client)
        .removeObject(any(RemoveObjectArgs.class));
    assertThatThrownBy(() -> storage.delete("sealed/test"))
        .hasMessageContaining("文件存储服务暂不可用")
        .hasMessageNotContaining("sensitive-provider-detail")
        .hasNoCause();
  }
}
