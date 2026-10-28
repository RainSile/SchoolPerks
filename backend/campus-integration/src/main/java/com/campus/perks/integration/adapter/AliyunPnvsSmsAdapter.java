package com.campus.perks.integration.adapter;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.AliyunPnvsProperties;
import com.campus.perks.integration.port.SmsSenderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** PNVS generates and verifies codes. Plaintext codes never enter local challenge storage. */
public final class AliyunPnvsSmsAdapter implements SmsSenderPort {
  public static final String TYPE = "ALIYUN_PNVS";
  private final AliyunPnvsProperties p;
  private final Client client;
  private final ObjectMapper json;

  public AliyunPnvsSmsAdapter(AliyunPnvsProperties properties, ObjectMapper json) {
    this(properties, json, createClient(properties));
  }

  public AliyunPnvsSmsAdapter(AliyunPnvsProperties properties, ObjectMapper json, Client client) {
    validate(properties);
    this.p = properties;
    this.json = json;
    this.client = java.util.Objects.requireNonNull(client);
  }

  private static void validate(AliyunPnvsProperties p) {
    if (p == null || !p.enabled())
      throw new IllegalStateException("PNVS must be explicitly enabled");
    URI endpoint;
    try {
      endpoint = URI.create(p.endpoint());
    } catch (RuntimeException e) {
      throw new IllegalStateException("Invalid PNVS endpoint");
    }
    if (!"https".equals(endpoint.getScheme())
        || endpoint.getHost() == null
        || endpoint.getUserInfo() != null
        || endpoint.getQuery() != null
        || endpoint.getFragment() != null
        || !Set.of("", "/").contains(endpoint.getPath()))
      throw new IllegalStateException("PNVS endpoint must be an HTTPS origin");
    for (String value :
        new String[] {
          p.accessKeyId(),
          p.accessKeySecret(),
          p.signName(),
          p.countryCode(),
          p.loginTemplateCode(),
          p.registerTemplateCode(),
          p.resetTemplateCode()
        })
      if (value == null || value.isBlank())
        throw new IllegalStateException("PNVS configuration is incomplete");
    var scopes = new java.util.HashSet<String>();
    for (String scope : new String[] {p.loginScheme(), p.registerScheme(), p.resetScheme()})
      if (scope == null || !scope.matches("[A-Za-z0-9_-]{1,20}") || !scopes.add(scope))
        throw new IllegalStateException(
            "PNVS schemes must be distinct, safe names of at most20 characters");
    if (!"86".equals(p.countryCode()))
      throw new IllegalStateException("Only configured domestic SMS is supported");
    for (Duration timeout : new Duration[] {p.connectTimeout(), p.readTimeout()})
      if (timeout == null
          || timeout.isNegative()
          || timeout.isZero()
          || timeout.compareTo(Duration.ofSeconds(30)) > 0)
        throw new IllegalStateException("PNVS timeouts must be positive and bounded");
  }

  private static Client createClient(AliyunPnvsProperties p) {
    validate(p);
    try {
      URI endpoint = URI.create(p.endpoint());
      var config =
          new com.aliyun.teaopenapi.models.Config()
              .setAccessKeyId(p.accessKeyId())
              .setAccessKeySecret(p.accessKeySecret())
              .setEndpoint(endpoint.getRawAuthority())
              .setProtocol("HTTPS")
              .setConnectTimeout(Math.toIntExact(p.connectTimeout().toMillis()))
              .setReadTimeout(Math.toIntExact(p.readTimeout().toMillis()));
      return new Client(config);
    } catch (Exception e) {
      throw new IllegalStateException("PNVS client initialization failed");
    }
  }

  private RuntimeOptions runtime() {
    return new RuntimeOptions()
        .setAutoretry(false)
        .setConnectTimeout(Math.toIntExact(p.connectTimeout().toMillis()))
        .setReadTimeout(Math.toIntExact(p.readTimeout().toMillis()));
  }

  public boolean available() {
    return true;
  }

  public boolean verifiesRemotely() {
    return true;
  }

  public String verifierType() {
    return TYPE;
  }

  private String scope(String purpose) {
    return switch (purpose) {
      case "LOGIN" -> p.loginScheme();
      case "REGISTER" -> p.registerScheme();
      case "RESET_PASSWORD" -> p.resetScheme();
      default -> throw new IllegalArgumentException("Unsupported SMS purpose");
    };
  }

  private String template(String purpose) {
    return switch (purpose) {
      case "LOGIN" -> p.loginTemplateCode();
      case "REGISTER" -> p.registerTemplateCode();
      case "RESET_PASSWORD" -> p.resetTemplateCode();
      default -> throw new IllegalArgumentException("Unsupported SMS purpose");
    };
  }

  public void sendVerificationCode(String phone, String purpose, String code, Duration validity) {
    throw new UnsupportedOperationException(
        "PNVS requires provider-generated verification challenges");
  }

  public Receipt startRemoteVerification(RemoteChallenge input) {
    String scope = scope(input.purpose());
    if (input.codeLength() < 6
        || input.codeLength() > 8
        || input.validity().isNegative()
        || input.validity().isZero()
        || input.validity().getSeconds() % 60 != 0)
      throw new IllegalArgumentException(
          "SMS code length and whole-minute validity must match the template");
    try {
      var request =
          new SendSmsVerifyCodeRequest()
              .setPhoneNumber(input.phone())
              .setCountryCode(p.countryCode())
              .setSignName(p.signName())
              .setTemplateCode(template(input.purpose()))
              .setTemplateParam(
                  json.writeValueAsString(
                      Map.of(
                          "code", "##code##", "min", Long.toString(input.validity().toMinutes()))))
              .setSchemeName(scope)
              .setOutId(input.id())
              .setCodeLength((long) input.codeLength())
              .setCodeType(1L)
              .setValidTime(input.validity().getSeconds())
              .setInterval(input.interval().getSeconds())
              .setDuplicatePolicy(1L)
              .setReturnVerifyCode(false)
              .setAutoRetry(0L);
      var response = client.sendSmsVerifyCodeWithOptions(request, runtime());
      var body = response == null ? null : response.getBody();
      if (body == null || !Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode()))
        throw providerFailure(body == null ? null : body.getCode());
      var model = body.getModel();
      if (model == null
          || model.getBizId() == null
          || model.getBizId().isBlank()
          || model.getBizId().length() > 256
          || !input.id().equals(model.getOutId())) throw unavailable();
      return new Receipt(TYPE, model.getBizId(), scope);
    } catch (ApplicationException e) {
      throw e;
    } catch (com.aliyun.tea.TeaException e) {
      throw providerFailure(e.getCode());
    } catch (Exception e) {
      throw unavailable();
    }
  }

  public boolean verifyRemoteCode(
      String phone,
      String purpose,
      String persistedScope,
      String reference,
      String challengeId,
      String code) {
    if (!scope(purpose).equals(persistedScope) || reference == null || reference.isBlank())
      throw unavailable();
    try {
      var request =
          new CheckSmsVerifyCodeRequest()
              .setPhoneNumber(phone)
              .setCountryCode(p.countryCode())
              .setSchemeName(persistedScope)
              .setOutId(challengeId)
              .setVerifyCode(code)
              .setCaseAuthPolicy(1L);
      var response = client.checkSmsVerifyCodeWithOptions(request, runtime());
      var body = response == null ? null : response.getBody();
      if (body == null || !Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode()))
        throw providerFailure(body == null ? null : body.getCode());
      if (body.getModel() == null || !challengeId.equals(body.getModel().getOutId()))
        throw unavailable();
      return "PASS".equals(body.getModel().getVerifyResult());
    } catch (ApplicationException e) {
      throw e;
    } catch (com.aliyun.tea.TeaException e) {
      if ("isv.ValidateFail".equals(e.getCode())) return false;
      throw providerFailure(e.getCode());
    } catch (Exception e) {
      throw unavailable();
    }
  }

  private ApplicationException providerFailure(String code) {
    return Set.of("FREQUENCY_FAIL", "BUSINESS_LIMIT_CONTROL").contains(code == null ? "" : code)
        ? new ApplicationException(
            HttpStatus.TOO_MANY_REQUESTS, "SMS_PROVIDER_RATE_LIMITED", "短信发送或核验过于频繁，请稍后重试")
        : unavailable();
  }

  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "SMS_PROVIDER_UNAVAILABLE", "短信服务暂不可用，请稍后重试");
  }
}
