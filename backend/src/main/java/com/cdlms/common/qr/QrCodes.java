package com.cdlms.common.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Map;

/** Draws QR codes for printed documents. The output is a PNG data URI, ready for an {@code <img>} in a PDF. */
public final class QrCodes {

    private static final int QUIET_ZONE_MODULES = 1;

    private QrCodes() {
    }

    /** A black-on-white QR code of {@code text}, about {@code size} pixels square, as {@code data:image/png;base64,…}. */
    public static String pngDataUri(String text, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, QUIET_ZONE_MODULES));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException e) {
            throw new IllegalArgumentException("Cannot draw a QR code for that text", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
