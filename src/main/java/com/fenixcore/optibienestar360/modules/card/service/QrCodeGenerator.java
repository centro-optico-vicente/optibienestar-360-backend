package com.fenixcore.optibienestar360.modules.card.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates QR codes as inline PNG {@code data:} URIs (ZXing, Apache-2.0). The
 * caller embeds the returned string straight into JSON / an {@code <img src>},
 * so nothing has to be persisted to object storage for the digital card.
 */
@Component
public class QrCodeGenerator {

    /**
     * Encodes {@code content} into a square PNG QR of {@code size}px and
     * returns it as a {@code data:image/png;base64,…} URI.
     *
     * @throws IllegalStateException if encoding fails (unexpected — the content
     *         is a short internal UUID that always fits)
     */
    public String toPngDataUri(String content, int size) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate QR code", e);
        }
    }
}
