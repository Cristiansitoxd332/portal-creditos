package com.portalcreditos.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cambio de contrasena. Politica: 8 a 64 caracteres, con al menos una minuscula,
 * una mayuscula, un numero y un caracter especial.
 */
public record CambiarPasswordRequest(
        @NotBlank(message = "La contrasena actual es obligatoria")
        String passwordActual,

        @NotBlank(message = "La nueva contrasena es obligatoria")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$",
                message = "La nueva contrasena debe tener entre 8 y 64 caracteres e incluir minuscula, mayuscula, numero y caracter especial")
        String passwordNueva,

        @NotBlank(message = "Debe confirmar la nueva contrasena")
        String confirmacion
) {
}
