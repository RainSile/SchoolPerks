package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.campus.perks.coupon.service.RedemptionCodes;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

class RedemptionQrTest {
  @Test
  void emittedSvgIsValidXmlAndDecodesToTheExactCredential() throws Exception {
    var codes = new RedemptionCodes();
    String code = "123." + codes.random();
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document =
        factory.newDocumentBuilder().parse(new InputSource(new StringReader(codes.qr(code))));
    var dimensions = document.getDocumentElement().getAttribute("viewBox").split(" ");
    int scale = 4;
    int width = Integer.parseInt(dimensions[2]) * scale;
    int height = Integer.parseInt(dimensions[3]) * scale;
    int[] pixels = new int[width * height];
    Arrays.fill(pixels, 0xffffff);
    String path =
        document
            .getElementsByTagName("path")
            .item(0)
            .getAttributes()
            .getNamedItem("d")
            .getNodeValue();
    var points = Pattern.compile("M([0-9]+) ([0-9]+)h1v1h-1z").matcher(path);
    while (points.find()) {
      int x = Integer.parseInt(points.group(1)) * scale;
      int y = Integer.parseInt(points.group(2)) * scale;
      for (int dy = 0; dy < scale; dy++) {
        Arrays.fill(pixels, (y + dy) * width + x, (y + dy) * width + x + scale, 0);
      }
    }
    var bitmap =
        new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
    assertThat(
            new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.PURE_BARCODE, true)).getText())
        .isEqualTo(code);
  }
}
