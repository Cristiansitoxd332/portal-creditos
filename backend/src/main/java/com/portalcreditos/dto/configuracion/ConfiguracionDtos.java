package com.portalcreditos.dto.configuracion;

import com.portalcreditos.entity.enums.TipoCanal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTO de canales de contacto y requisitos de levantamiento (configurables por el admin). */
public final class ConfiguracionDtos {

    private ConfiguracionDtos() {
    }

    public record CanalRequest(
            @NotNull(message = "El tipo de canal es obligatorio") TipoCanal tipo,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String nombre,
            @NotBlank(message = "El valor es obligatorio") @Size(max = 200) String valor,
            @Size(max = 300) String enlace,
            @Size(max = 150) String horario,
            @Min(0) @Max(999) Integer orden,
            Boolean activo
    ) {
    }

    public record CanalInfo(Long id, TipoCanal tipo, String nombre, String valor, String enlace,
                            String horario, Integer orden, boolean activo) {
    }

    public record RequisitoRequest(
            @NotBlank(message = "El titulo es obligatorio") @Size(max = 150) String titulo,
            @NotBlank(message = "La descripcion es obligatoria") @Size(max = 500) String descripcion,
            @Min(0) @Max(999) Integer orden,
            Boolean activo
    ) {
    }

    public record RequisitoInfo(Long id, String titulo, String descripcion, Integer orden, boolean activo) {
    }
}
