package com.portalcreditos.dto.documento;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO del paz y salvo y de su verificacion publica. */
public final class DocumentoDtos {

    private DocumentoDtos() {
    }

    /** Indica si el cliente puede descargar el paz y salvo y por que. */
    public record PazYSalvoEstado(
            boolean disponible,
            String mensaje,
            String codigoVerificacion,
            LocalDateTime fechaEmision
    ) {
    }

    /** Resultado de verificar un codigo de paz y salvo. Los datos personales van enmascarados. */
    public record VerificacionPazYSalvo(
            boolean valido,
            String codigoVerificacion,
            String numeroCredito,
            String titular,
            String documento,
            String placa,
            LocalDate fechaCancelacion,
            LocalDateTime fechaEmision,
            String aviso
    ) {
    }
}
