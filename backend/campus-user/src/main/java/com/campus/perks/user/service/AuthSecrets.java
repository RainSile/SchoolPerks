package com.campus.perks.user.service;

import com.campus.perks.user.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** HMAC protects low-entropy OTPs against offline guessing; AES-GCM encrypts campus identity. */
@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AuthSecrets {
  private final SecureRandom random = new SecureRandom();
  private final byte[] hashKey;
  private final byte[] identityKey;

  public AuthSecrets(AuthProperties properties) {
    hashKey = key(properties.codeHashSecretBase64());
    identityKey = key(properties.identityKeyBase64());
  }

  private byte[] key(String encoded) {
    try {
      byte[] decoded = Base64.getDecoder().decode(encoded);
      if (decoded.length != 32) throw new IllegalArgumentException();
      return decoded;
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalStateException(
          "OTP_HASH_SECRET_BASE64 and IDENTITY_KEY_BASE64 require 32 random bytes each");
    }
  }

  public String code(int length) {
    var code = new StringBuilder();
    for (int i = 0; i < length; i++) code.append(random.nextInt(10));
    return code.toString();
  }

  public String token() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hash(String value) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC unavailable");
    }
  }

  public boolean matches(String expected, String value) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        hash(value).getBytes(StandardCharsets.US_ASCII));
  }

  public String encryptIdentity(String userId, String json) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(identityKey, "AES"),
          new GCMParameterSpec(128, iv));
      cipher.updateAAD(userId.getBytes(StandardCharsets.UTF_8));
      byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(iv)
          + ":"
          + Base64.getEncoder().encodeToString(encrypted);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Identity encryption unavailable");
    }
  }

  public String decryptIdentity(String userId, String encoded) {
    try {
      String[] parts = encoded.split(":", -1);
      if (parts.length != 2) throw new GeneralSecurityException();
      byte[] iv = Base64.getDecoder().decode(parts[0]);
      if (iv.length != 12) throw new GeneralSecurityException();
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(identityKey, "AES"),
          new GCMParameterSpec(128, iv));
      cipher.updateAAD(userId.getBytes(StandardCharsets.UTF_8));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new com.campus.perks.common.exception.ApplicationException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
          "IDENTITY_UNAVAILABLE",
          "身份资料暂时无法解密，请联系平台处理");
    }
  }
}
