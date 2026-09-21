package com.babybuddypocket.app;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/** Only the luminance plane is read; decoding never performs network I/O. */
final class QrDecoder {
  static String decode(byte[] nv21, int width, int height) {
    if (width <= 0 || height <= 0 || (long) width * height > nv21.length) return null;
    LuminanceSource source =
        new PlanarYUVLuminanceSource(nv21, width, height, 0, 0, width, height, false);
    QRCodeReader reader = new QRCodeReader();
    try {
      return reader.decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    } catch (ReaderException ignored) {
      return null;
    } finally {
      reader.reset();
    }
  }
}
