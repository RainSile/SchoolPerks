package com.campus.perks.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.adapter.AliyunPnvsSmsAdapter;
import com.campus.perks.integration.config.AliyunPnvsProperties;
import com.campus.perks.integration.port.SmsSenderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class AliyunPnvsSmsAdapterTest {
  static AliyunPnvsProperties properties() {
    return new AliyunPnvsProperties(
        true,
        "https://dypnsapi.aliyuncs.com",
        "fixture-access-id",
        "fixture-access-secret",
        "测试签名",
        "86",
        "100001",
        "100001",
        "100003",
        "campus-login",
        "campus-register",
        "campus-reset",
        Duration.ofSeconds(2),
        Duration.ofSeconds(3));
  }

  @Test
  void sdkLoadsAlongsideTheExistingHttpStackAndNeverPrintsConfigurationSecrets() {
    assertThat(new AliyunPnvsSmsAdapter(properties(), new ObjectMapper()).available()).isTrue();
    assertThat(properties().toString()).doesNotContain("fixture-access", "测试签名");
    assertThat(
            new SmsSenderPort.RemoteChallenge(
                    "id", "phone", "LOGIN", 6, Duration.ofMinutes(5), Duration.ofSeconds(60))
                .toString())
        .doesNotContain("phone");
  }

  private SendSmsVerifyCodeResponse sendResponse(String id) {
    return new SendSmsVerifyCodeResponse()
        .setBody(
            new SendSmsVerifyCodeResponseBody()
                .setSuccess(true)
                .setCode("OK")
                .setModel(
                    new SendSmsVerifyCodeResponseBody.SendSmsVerifyCodeResponseBodyModel()
                        .setBizId("fixture-reference")
                        .setOutId(id)));
  }

  private CheckSmsVerifyCodeResponse checkResponse(String id, String result) {
    return new CheckSmsVerifyCodeResponse()
        .setBody(
            new CheckSmsVerifyCodeResponseBody()
                .setSuccess(true)
                .setCode("OK")
                .setModel(
                    new CheckSmsVerifyCodeResponseBody.CheckSmsVerifyCodeResponseBodyModel()
                        .setOutId(id)
                        .setVerifyResult(result)));
  }

  @Test
  void sendsProviderGeneratedCodesAndSeparatesAllThreePurposesWithoutAutomaticRetries()
      throws Exception {
    Client client = mock(Client.class);
    List<SendSmsVerifyCodeRequest> sent = new ArrayList<>();
    when(client.sendSmsVerifyCodeWithOptions(any(), any()))
        .thenAnswer(
            call -> {
              SendSmsVerifyCodeRequest r = call.getArgument(0);
              sent.add(r);
              return sendResponse(r.getOutId());
            });
    var adapter = new AliyunPnvsSmsAdapter(properties(), new ObjectMapper(), client);
    for (String purpose : List.of("LOGIN", "REGISTER", "RESET_PASSWORD")) {
      var input =
          new SmsSenderPort.RemoteChallenge(
              UUID.randomUUID().toString(),
              "13800000000",
              purpose,
              6,
              Duration.ofMinutes(5),
              Duration.ofSeconds(60));
      var receipt = adapter.startRemoteVerification(input);
      assertThat(receipt.verifierType()).isEqualTo("ALIYUN_PNVS");
    }
    assertThat(sent).extracting(SendSmsVerifyCodeRequest::getSchemeName).doesNotHaveDuplicates();
    assertThat(sent)
        .extracting(SendSmsVerifyCodeRequest::getTemplateCode)
        .containsExactly("100001", "100001", "100003");
    for (var request : sent) {
      assertThat(new ObjectMapper().readTree(request.getTemplateParam()).path("code").asText())
          .isEqualTo("##code##");
      assertThat(request.getReturnVerifyCode()).isFalse();
      assertThat(request.getAutoRetry()).isZero();
      assertThat(request.getCodeLength()).isEqualTo(6L);
      assertThat(request.getValidTime()).isEqualTo(300L);
      assertThat(request.getDuplicatePolicy()).isEqualTo(1L);
    }
    assertThatThrownBy(
            () ->
                adapter.sendVerificationCode(
                    "13800000000", "LOGIN", "123456", Duration.ofMinutes(5)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void acceptsOnlyTheVerifyResultPassAndPreservesPhoneScopeAndChallengeBinding() throws Exception {
    Client client = mock(Client.class);
    when(client.checkSmsVerifyCodeWithOptions(any(), any()))
        .thenReturn(checkResponse("challenge", "UNKNOWN"), checkResponse("challenge", "PASS"));
    var adapter = new AliyunPnvsSmsAdapter(properties(), new ObjectMapper(), client);
    assertThat(
            adapter.verifyRemoteCode(
                "13800000000", "LOGIN", "campus-login", "ref", "challenge", "123456"))
        .isFalse();
    assertThat(
            adapter.verifyRemoteCode(
                "13800000000", "LOGIN", "campus-login", "ref", "challenge", "123456"))
        .isTrue();
    var capture = org.mockito.ArgumentCaptor.forClass(CheckSmsVerifyCodeRequest.class);
    verify(client, times(2)).checkSmsVerifyCodeWithOptions(capture.capture(), any());
    assertThat(capture.getValue().getOutId()).isEqualTo("challenge");
    assertThat(capture.getValue().getPhoneNumber()).isEqualTo("13800000000");
    assertThatThrownBy(
            () ->
                adapter.verifyRemoteCode(
                    "13800000000", "REGISTER", "campus-login", "ref", "challenge", "123456"))
        .isInstanceOf(ApplicationException.class);
    verifyNoMoreInteractions(client);
  }

  @Test
  void failsClosedForMissingOrMismatchedResponsesAndDoesNotLeakProviderMessages() throws Exception {
    Client client = mock(Client.class);
    when(client.checkSmsVerifyCodeWithOptions(any(), any()))
        .thenReturn(checkResponse("different", "PASS"), new CheckSmsVerifyCodeResponse());
    var adapter = new AliyunPnvsSmsAdapter(properties(), new ObjectMapper(), client);
    for (int i = 0; i < 2; i++)
      assertThatThrownBy(
              () ->
                  adapter.verifyRemoteCode(
                      "13800000000", "LOGIN", "campus-login", "ref", "challenge", "123456"))
          .isInstanceOf(ApplicationException.class)
          .hasMessageNotContaining("fixture-access");
    when(client.sendSmsVerifyCodeWithOptions(any(), any()))
        .thenReturn(
            new SendSmsVerifyCodeResponse()
                .setBody(
                    new SendSmsVerifyCodeResponseBody()
                        .setSuccess(false)
                        .setCode("FREQUENCY_FAIL")
                        .setMessage("private provider message")));
    assertThatThrownBy(
            () ->
                adapter.startRemoteVerification(
                    new SmsSenderPort.RemoteChallenge(
                        "id",
                        "13800000000",
                        "LOGIN",
                        6,
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(60))))
        .isInstanceOf(ApplicationException.class)
        .hasMessageNotContaining("private provider");
  }

  @Test
  void upstreamInvalidCodeIsAnAuthenticationFailureNotAServiceOutage() throws Exception {
    Client client = mock(Client.class);
    when(client.checkSmsVerifyCodeWithOptions(any(), any()))
        .thenThrow(
            new com.aliyun.tea.TeaException(
                Map.of("code", "isv.ValidateFail", "message", "private cloud validation detail")));
    var adapter = new AliyunPnvsSmsAdapter(properties(), new ObjectMapper(), client);
    assertThat(
            adapter.verifyRemoteCode("13800000000", "LOGIN", "campus-login", "ref", "id", "123456"))
        .isFalse();
  }
}
