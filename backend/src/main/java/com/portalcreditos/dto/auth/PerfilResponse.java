package com.portalcreditos.dto.auth;

import java.time.LocalDateTime;

/** Datos basicos del usuario autenticado. */
public record PerfilResponse(
        String username,
        String rol,
        String nombre,
        Long clienteId,
        String correo,
        boolean debeCambiarPassword,
        LocalDateTime ultimoAcceso
) {
}
