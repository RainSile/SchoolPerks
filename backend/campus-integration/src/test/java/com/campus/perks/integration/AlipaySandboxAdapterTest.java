package com.campus.perks.integration;

import static org.assertj.core.api.Assertions.*;

import com.alipay.api.internal.util.AlipaySignature;
import com.campus.perks.integration.adapter.AlipaySandboxAdapter;
import com.campus.perks.integration.config.AlipaySandboxProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** Real RSA2 SDK execution using ephemeral keys; never contacts any payment gateway. */
class AlipaySandboxAdapterTest {
  @Test
  void paymentRuntimeHasOneImplementationOfSharedLibraries() throws Exception {
    var loader = getClass().getClassLoader();
    for (String resource :
        List.of(
            "org/bouncycastle/jce/provider/BouncyCastleProvider.class",
            "org/apache/commons/logging/LogFactory.class",
            "org/dom4j/Document.class"))
      assertThat(Collections.list(loader.getResources(resource))).as(resource).hasSize(1);
    assertThat(new okhttp3.OkHttpClient.Builder().build()).isNotNull();
  }

  private String privateKey, publicKey;
  private AlipaySandboxAdapter adapter;

  private AlipaySandboxProperties config(String gateway, boolean enabled) {
    return new AlipaySandboxProperties(
        enabled,
        gateway,
        "test-app",
        "test-seller",
        privateKey,
        publicKey,
        "https://callback.example.invalid/api/v1/payment-notifications/alipay-sandbox",
        Duration.ofSeconds(1),
        Duration.ofSeconds(2));
  }

  @BeforeEach
  void keys() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    adapter =
        new AlipaySandboxAdapter(
            config("https://openapi.alipaydev.com/gateway.do", true), new ObjectMapper());
  }

  private TreeMap<String, String> signed(String app, String seller) throws Exception {
    var p = new TreeMap<String, String>();
    p.put("app_id", app);
    p.put("seller_id", seller);
    p.put("notify_id", "notification-1");
    p.put("out_trade_no", "CP123");
    p.put("trade_no", "T123");
    p.put("trade_status", "TRADE_SUCCESS");
    p.put("total_amount", "19.90");
    p.put("charset", "utf-8");
    p.put(
        "sign",
        AlipaySignature.rsaSign(AlipaySignature.getSignContent(p), privateKey, "UTF-8", "RSA2"));
    p.put("sign_type", "RSA2");
    return p;
  }

  @Test
  void validRsa2NotificationIsVerifiedAndTamperingRejected() throws Exception {
    var p = signed("test-app", "test-seller");
    assertThat(adapter.verify(p).amount()).isEqualByComparingTo("19.90");
    p.put("total_amount", "0.01");
    assertThatThrownBy(() -> adapter.verify(p)).hasMessageContaining("校验失败");
    assertThatThrownBy(() -> adapter.verify(signed("another-app", "test-seller")))
        .hasMessageContaining("校验失败");
    assertThatThrownBy(() -> adapter.verify(signed("test-app", "another-seller")))
        .hasMessageContaining("校验失败");
  }

  @Test
  void checkoutIsSignedOfficialSandboxUrlWithExactServerPrice() throws Exception {
    String url =
        adapter.checkout(
            "CP123", new BigDecimal("19.90"), "Campus test", Instant.now().plusSeconds(900));
    var uri = URI.create(url);
    assertThat(uri.getHost()).isEqualTo("openapi.alipaydev.com");
    var p = new TreeMap<String, String>();
    for (String entry : uri.getRawQuery().split("&")) {
      var pair = entry.split("=", 2);
      p.put(
          URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
          URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
    }
    assertThat(new ObjectMapper().readTree(p.get("biz_content")).get("total_amount").asText())
        .isEqualTo("19.90");
    String signature = p.remove("sign");
    assertThat(
            AlipaySignature.rsaCheck(
                AlipaySignature.getSignContent(p), signature, publicKey, "UTF-8", "RSA2"))
        .isTrue();
  }

  @Test
  void productionGatewayAndMissingConfigurationCannotBeEnabled() {
    assertThatThrownBy(
            () ->
                new AlipaySandboxAdapter(
                    config("https://openapi.alipay.com/gateway.do", true), new ObjectMapper()))
        .hasMessageContaining("sandbox");
    var disabled = new AlipaySandboxAdapter(config("", false), new ObjectMapper());
    assertThat(disabled.available()).isFalse();
    assertThatThrownBy(() -> disabled.checkout("CP123", BigDecimal.ONE, "test", Instant.now()))
        .hasMessageContaining("尚未配置");
  }
}
