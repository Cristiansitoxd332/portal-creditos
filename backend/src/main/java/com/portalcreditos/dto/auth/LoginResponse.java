package com.portalcreditos.dto.auth;

import java.time.Instant;

/** Respuesta de un inicio de sesion exitoso. */
public record LoginResponse(
        String token,
        String tipo,
        Instant expiraEn,
        String username,
        String rol,
        String nombre,
        boolean debeCambiarPassword
) {
}
