package com.campus.perks.coupon.service;

import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RedemptionCodes {
  private final SecureRandom random = new SecureRandom();

  public String random() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  public boolean matches(String code, String expected) {
    return MessageDigest.isEqual(
        hash(code).getBytes(StandardCharsets.US_ASCII),
        expected.getBytes(StandardCharsets.US_ASCII));
  }

  public String qr(String code) {
    try {
      var matrix =
          new QRCodeWriter()
              .encode(code, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 4));
      var path = new StringBuilder();
      for (int y = 0; y < matrix.getHeight(); y++)
        for (int x = 0; x < matrix.getWidth(); x++)
          if (matrix.get(x, y)) path.append("M").append(x).append(' ').append(y).append("h1v1h-1z");
      return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 "
          + matrix.getWidth()
          + " "
          + matrix.getHeight()
          + "' shape-rendering='crispEdges'><rect width='100%' height='100%' fill='white'/><path"
          + " d='"
          + path
          + "' fill='black'/></svg>";
    } catch (WriterException e) {
      throw new IllegalStateException("QR generation unavailable");
    }
  }
}
