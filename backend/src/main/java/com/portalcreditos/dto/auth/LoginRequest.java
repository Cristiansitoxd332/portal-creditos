package com.portalcreditos.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos de inicio de sesion. Para los clientes, el usuario es su numero de documento. */
public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 50, message = "El usuario no puede superar 50 caracteres")
        String username,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(max = 64, message = "La contrasena no puede superar 64 caracteres")
        String password
) {
}
