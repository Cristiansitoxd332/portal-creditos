package com.portalcreditos.dto.pago;

import com.portalcreditos.dto.credito.EstadoVisibleCuota;
import com.portalcreditos.entity.enums.CanalPago;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoPago;
import com.portalcreditos.entity.enums.MedioPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Objetos de entrada y salida del modulo de pagos (simulados). */
public final class PagoDtos {

    private PagoDtos() {
    }

    /** Solicitud de pago. simularRechazo solo existe en el prototipo para demostrar el flujo de rechazo. */
    public record PagoRequest(
            @NotNull(message = "El valor es obligatorio")
            @DecimalMin(value = "1", message = "El valor debe ser mayor que cero")
            @Digits(integer = 13, fraction = 2, message = "El valor no tiene un formato valido")
            BigDecimal valor,

            @NotNull(message = "El medio de pago es obligatorio")
            MedioPago medioPago,

            Boolean simularRechazo,

            @Size(max = 200, message = "La observacion no puede superar 200 caracteres")
            String observacion
    ) {
    }

    /** Valores sugeridos para pagar (botones rapidos del portal). */
    public record OpcionesPago(
            Long creditoId,
            String numeroCredito,
            BigDecimal valorVencido,
            int cuotasVencidas,
            BigDecimal valorHastaProximaCuota,
            BigDecimal valorMinimo,
            BigDecimal valorMaximo,
            List<MedioPago> mediosDisponibles,
            String aviso
    ) {
    }

    /** Detalle de la aplicacion de un pago a una cuota. */
    public record AplicacionInfo(
            int numeroCuota,
            BigDecimal valorAplicado,
            EstadoVisibleCuota estadoCuota
    ) {
    }

    /** Comprobante de pago. */
    public record Comprobante(
            Long pagoId,
            String referencia,
            String codigoAutorizacion,
            LocalDateTime fecha,
            String numeroCredito,
            String cliente,
            String documentoCliente,
            BigDecimal valor,
            MedioPago medioPago,
            CanalPago canal,
            EstadoPago estado,
            String mensaje,
            String pasarela,
            BigDecimal saldoCapitalAnterior,
            BigDecimal saldoCapitalNuevo,
            EstadoCredito estadoCredito,
            List<AplicacionInfo> aplicaciones,
            String aviso
    ) {
    }

    /** Fila del historial de pagos. */
    public record PagoHistorial(
            Long pagoId,
            String referencia,
            LocalDateTime fecha,
            BigDecimal valor,
            MedioPago medioPago,
            CanalPago canal,
            EstadoPago estado,
            String cuotasAplicadas
    ) {
    }
}
