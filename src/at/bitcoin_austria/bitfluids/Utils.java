package at.bitcoin_austria.bitfluids;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Hashtable;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class Utils {
  public final static QRCodeWriter     QR_CODE_WRITER = new QRCodeWriter();
  public final static String           MTGOX_BTCEUR   = "http://mtgox.com/api/1/BTCEUR/public/ticker";
  public final static SimpleDateFormat timeFmt        = new SimpleDateFormat("HH:mm:ss");

  public final static DecimalFormat    uriDF          = new DecimalFormat("0.########");
  private final static StringBuilder   uriSB          = new StringBuilder();
  public final static DecimalFormat    eurDF          = new DecimalFormat("0.00 €");

  /**
   * wrapper for {@link #makeBitcoinUri(String, Double, String)}
   * 
   * @param addr
   * @param amount
   * @return
   */
  final static String makeBitcoinUri(String addr, Double amount) {
    return makeBitcoinUri(addr, amount, null);
  }

  /**
   * we aim for the first example at <a
   * href="https://en.bitcoin.it/wiki/URI_Scheme">bitcoin uri scheme</a>.
   * 
   * i.e.
   * 
   * <pre>
   * bitcoin:1NS17iag9jJgTHD1VXjvLCEnZuQ3rJED9L?amount=20.3X8&label=Luke-Jr
   * </pre>
   * 
   * @param amount
   *          in BTC
   * @param label
   *          the label parameter, could be "null"
   * @return
   */
  final static String makeBitcoinUri(String addr, Double amount, String label) {
    uriSB.setLength(0);
    uriSB.append("bitcoin:").append(addr).append("?");
    // X8 as part of the number format doesn't work :(
    uriSB.append("amount=").append(uriDF.format(amount)).append("X8");
    if (label != null)
      uriSB.append("?").append("label=").append(label);
    return uriSB.toString();
  }

  static Bitmap getQRCodeBitmap(final String url, final int size) {
    try {
      final Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
      hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
      final BitMatrix result = QR_CODE_WRITER.encode(url, BarcodeFormat.QR_CODE, size, size, hints);

      final int width = result.getWidth();
      final int height = result.getHeight();
      final int[] pixels = new int[width * height];

      for (int y = 0; y < height; y++) {
        final int offset = y * width;
        for (int x = 0; x < width; x++) {
          pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
        }
      }

      final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
      return bitmap;
    } catch (final WriterException x) {
      x.printStackTrace();
      return null;
    }
  }

}
