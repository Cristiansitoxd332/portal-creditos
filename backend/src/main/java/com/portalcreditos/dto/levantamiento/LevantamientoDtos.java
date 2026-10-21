package com.portalcreditos.dto.levantamiento;

import com.portalcreditos.entity.enums.EstadoPrenda;
import com.portalcreditos.entity.enums.EstadoSolicitud;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** DTO del modulo de levantamiento de prenda (solicitudes simuladas). */
public final class LevantamientoDtos {

    private LevantamientoDtos() {
    }

    public record SolicitudRequest(
            @Size(max = 500, message = "La observacion no puede superar 500 caracteres")
            String observacion
    ) {
    }

    public record RespuestaRequest(
            @NotNull(message = "El nuevo estado es obligatorio")
            EstadoSolicitud estado,

            @Size(max = 500, message = "La respuesta no puede superar 500 caracteres")
            String respuesta
    ) {
    }

    /** Un paso de la linea de tiempo ("rastreo") de la solicitud. */
    public record PasoSeguimiento(
            String nombre,
            boolean completado,
            LocalDateTime fecha
    ) {
    }

    public record SolicitudInfo(
            Long id,
            String numeroRadicado,
            Long creditoId,
            String numeroCredito,
            String cliente,
            String documentoCliente,
            String placa,
            LocalDateTime fechaSolicitud,
            EstadoSolicitud estado,
            String observacionCliente,
            String respuestaAdmin,
            LocalDateTime fechaRespuesta,
            List<PasoSeguimiento> seguimiento
    ) {
    }

    /** Estado del levantamiento para un credito del cliente. */
    public record EstadoLevantamiento(
            Long creditoId,
            String numeroCredito,
            EstadoPrenda estadoPrenda,
            boolean puedeSolicitar,
            String mensaje,
            List<SolicitudInfo> solicitudes,
            String aviso
    ) {
    }
}
