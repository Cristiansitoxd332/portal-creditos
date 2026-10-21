package com.portalcreditos.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/** Genera la matriz de un codigo QR (cuadros negros y blancos) con la libreria ZXing. */
@Service
public class QrService {

    public BitMatrix generar(String contenido) {
        Map<EncodeHintType, Object> opciones = new EnumMap<>(EncodeHintType.class);
        opciones.put(EncodeHintType.MARGIN, 0);
        opciones.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        opciones.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            return new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, opciones);
        } catch (WriterException e) {
            throw new IllegalStateException("No fue posible generar el codigo QR", e);
        }
    }
}
