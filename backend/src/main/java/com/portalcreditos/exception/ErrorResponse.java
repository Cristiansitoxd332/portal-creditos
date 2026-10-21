package com.portalcreditos.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato unico de todas las respuestas de error de la API.
 * Asi el frontend siempre sabe donde leer el mensaje.
 */
public record ErrorResponse(
        LocalDateTime fecha,
        int estado,
        String error,
        String mensaje,
        String ruta,
        Map<String, String> detalles
) {
    public static ErrorResponse de(int estado, String error, String mensaje, String ruta) {
        return new ErrorResponse(LocalDateTime.now(), estado, error, mensaje, ruta, null);
    }
}
