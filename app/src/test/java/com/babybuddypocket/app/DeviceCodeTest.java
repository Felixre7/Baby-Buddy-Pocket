package com.babybuddypocket.app;

import static org.junit.Assert.*;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Arrays;
import org.junit.Test;

public class DeviceCodeTest {
  private static final String TOKEN = "0123456789abcdef0123456789abcdef01234567";

  static String payload(String url) {
    return "BABYBUDDY-LOGIN:{\"url\":\""
        + url
        + "\",\"api_key\":\""
        + TOKEN
        + "\",\"session_cookies\":{\"sessionid\":\"ignored-browser-cookie\"}}";
  }

  @Test
  public void upstreamFormatExtractsApiCredentialsOnly() {
    DeviceCode code = DeviceCode.parse("\n\n" + payload("https://baby.example/baby/") + "\n");
    assertEquals("https://baby.example/baby/api/", code.server);
    assertEquals(TOKEN, code.token);
  }

  @Test
  public void apiUrlIsNotDuplicated() {
    assertEquals(
        "https://baby.example/api/", DeviceCode.parse(payload("https://baby.example/api/")).server);
  }

  @Test
  public void unsafeUrlsAreRejectedWithoutLeakingContents() {
    for (String url :
        new String[] {
          "http://baby.example",
          "https://user:password@baby.example",
          "https://baby.example/?token=private",
          "https://baby.example/#fragment",
          "https://baby.example/../escape",
          "file:///tmp/key"
        }) rejected(payload(url));
  }

  @Test
  public void unrelatedOrMalformedQrCodesAreRejected() {
    for (String input :
        new String[] {
          null,
          "",
          "https://baby.example",
          "BABYBUDDY-LOGIN:{}",
          "BABYBUDDY-LOGIN:broken",
          payload("https://baby.example").replace("BABYBUDDY-LOGIN:", ""),
          payload("https://baby.example").replace(TOKEN, "short"),
          payload("https://baby.example").replace(TOKEN, TOKEN + "\\r\\nHeader: injected")
        }) rejected(input);
  }

  @Test
  public void oversizedPayloadIsRejected() {
    rejected("BABYBUDDY-LOGIN:" + "x".repeat(16384));
  }

  @Test
  public void cameraLuminanceDecodesRealQrWithSessionCookie() throws Exception {
    String raw = payload("https://baby.example/api/");
    BitMatrix matrix = new QRCodeWriter().encode(raw, BarcodeFormat.QR_CODE, 600, 600);
    byte[] frame = new byte[600 * 600 * 3 / 2];
    Arrays.fill(frame, (byte) 128);
    for (int y = 0; y < 600; y++)
      for (int x = 0; x < 600; x++) frame[y * 600 + x] = (byte) (matrix.get(x, y) ? 0 : 255);
    assertEquals(raw, QrDecoder.decode(frame, 600, 600));
    byte[] rotated = frame.clone();
    for (int y = 0; y < 600; y++)
      for (int x = 0; x < 600; x++) rotated[x * 600 + 599 - y] = frame[y * 600 + x];
    assertEquals(raw, QrDecoder.decode(rotated, 600, 600));
  }

  @Test
  public void blankOrIncompleteFrameDoesNotDecode() {
    byte[] frame = new byte[100 * 100];
    Arrays.fill(frame, (byte) 255);
    assertNull(QrDecoder.decode(frame, 100, 100));
    assertNull(QrDecoder.decode(new byte[1], 100, 100));
  }

  private void rejected(String value) {
    try {
      DeviceCode.parse(value);
      fail("Expected a rejected code");
    } catch (IllegalArgumentException e) {
      assertFalse(e.getMessage().contains(TOKEN));
      assertFalse(e.getMessage().contains("ignored-browser-cookie"));
      assertFalse(e.getMessage().contains("password@"));
    }
  }
}
