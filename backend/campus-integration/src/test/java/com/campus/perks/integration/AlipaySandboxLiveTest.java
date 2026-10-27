package com.campus.perks.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Explicitly enabled, read-only sandbox credential checks; never creates or pays an order. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_ALIPAY_LIVE_TEST", matches = "true")
class AlipaySandboxLiveTest {
  private String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new AssertionError("Missing private setting: " + name);
    return value;
  }

  @Test
  void configuredApplicationPrivateKeySignsAgainstTheMatchingPublicKey() throws Exception {
    String challenge = "campus-sandbox-probe-" + UUID.randomUUID();
    String signature =
        AlipaySignature.rsaSign(challenge, required("ALIPAY_SANDBOX_PRIVATE_KEY"), "UTF-8", "RSA2");
    assertThat(
            AlipaySignature.rsaCheck(
                challenge, signature, required("CAMPUS_ALIPAY_APP_PUBLIC_KEY"), "UTF-8", "RSA2"))
        .isTrue();
  }

  @Test
  void sandboxAcceptsSignedReadOnlyQueryAndItsResponsePassesProviderSignatureCheck() {
    try {
      URI gateway = URI.create(required("ALIPAY_SANDBOX_GATEWAY"));
      assertThat(
              Set.of(
                  "https://openapi.alipaydev.com/gateway.do",
                  "https://openapi-sandbox.dl.alipaydev.com/gateway.do"))
          .contains(gateway.toString());
      var config = new AlipayConfig();
      config.setServerUrl(gateway.toString());
      config.setAppId(required("ALIPAY_SANDBOX_APP_ID"));
      config.setPrivateKey(required("ALIPAY_SANDBOX_PRIVATE_KEY"));
      config.setAlipayPublicKey(required("ALIPAY_SANDBOX_PUBLIC_KEY"));
      config.setSignType("RSA2");
      config.setCharset("UTF-8");
      config.setFormat("json");
      config.setConnectTimeout(5000);
      config.setReadTimeout(10000);
      String proxy = System.getenv("CAMPUS_PAYMENT_PROXY_HOST");
      if (proxy != null && !proxy.isBlank()) {
        config.setProxyHost(proxy);
        config.setProxyPort(Integer.parseInt(required("CAMPUS_PAYMENT_PROXY_PORT")));
      }
      var request = new AlipayTradeQueryRequest();
      request.setBizContent(
          "{\"out_trade_no\":\"CPCHECK" + UUID.randomUUID().toString().replace("-", "") + "\"}");
      var response = new DefaultAlipayClient(config).execute(request);
      assertThat(response.getCode())
          .as("A random nonexistent order must be an authenticated business error")
          .isEqualTo("40004");
      assertThat(response.getSubCode()).isEqualTo("ACQ.TRADE_NOT_EXIST");
      var json = new ObjectMapper();
      String signature = json.readTree(response.getBody()).path("sign").asText();
      assertThat(signature.isBlank()).as("The gateway must sign its response").isFalse();
      String content = null;
      try (var parser = json.getFactory().createParser(response.getBody())) {
        assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          String field = parser.currentName();
          parser.nextToken();
          int start = (int) parser.currentTokenLocation().getCharOffset();
          parser.skipChildren();
          if ("alipay_trade_query_response".equals(field)) {
            content =
                response.getBody().substring(start, (int) parser.currentLocation().getCharOffset());
          }
        }
      }
      assertThat(content != null).as("Response contains the original signed JSON span").isTrue();
      assertThat(
              AlipaySignature.rsaCheck(
                  content, signature, required("ALIPAY_SANDBOX_PUBLIC_KEY"), "UTF-8", "RSA2"))
          .as("Actual sandbox response signature")
          .isTrue();
    } catch (Exception failure) {
      // SDK exceptions may contain signed requests; do not attach provider messages or causes.
      throw new AssertionError(
          "Live sandbox query failed ("
              + failure.getClass().getSimpleName()
              + "); private request details omitted");
    }
  }
}
