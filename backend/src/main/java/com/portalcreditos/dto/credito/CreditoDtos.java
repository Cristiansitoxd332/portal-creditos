package com.portalcreditos.dto.credito;

import com.portalcreditos.entity.enums.ClaseVehiculo;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoPrenda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Objetos de respuesta (DTO) del modulo de creditos.
 * Las entidades nunca se envian directamente al frontend: los DTO exponen solo lo necesario.
 */
public final class CreditoDtos {

    private CreditoDtos() {
    }

    /** Proxima cuota por pagar. */
    public record ProximaCuota(
            int numeroCuota,
            LocalDate fechaVencimiento,
            BigDecimal valor,
            int diasRestantes
    ) {
    }

    /** Tarjeta resumen de un credito (lista "Mis creditos" y listado del administrador). */
    public record CreditoResumen(
            Long id,
            String numeroCredito,
            String cliente,
            String documentoCliente,
            String vehiculo,
            String placa,
            BigDecimal valorFinanciado,
            Integer plazoMeses,
            BigDecimal tasaInteresMensual,
            BigDecimal valorCuota,
            BigDecimal saldoCapital,
            EstadoCredito estado,
            Semaforo semaforo,
            EstadoPrenda estadoPrenda,
            int cuotasPagadas,
            int cuotasPendientes,
            int cuotasVencidas,
            BigDecimal valorVencido,
            int diasMora,
            BigDecimal porcentajePagado,
            ProximaCuota proximaCuota
    ) {
    }

    /** Datos del vehiculo financiado. */
    public record VehiculoInfo(
            String placa,
            String marca,
            String linea,
            Short modelo,
            String color,
            ClaseVehiculo clase,
            String numeroMotor,
            String numeroChasis
    ) {
    }

    /** Detalle completo de un credito. */
    public record CreditoDetalle(
            CreditoResumen resumen,
            BigDecimal valorVehiculo,
            BigDecimal cuotaInicial,
            LocalDate fechaDesembolso,
            LocalDate fechaPrimeraCuota,
            LocalDate fechaCancelacion,
            BigDecimal capitalPagado,
            BigDecimal interesesPagados,
            BigDecimal totalPagado,
            BigDecimal totalInteresesPlan,
            BigDecimal totalPagarPlan,
            VehiculoInfo vehiculo
    ) {
    }

    /** Una fila del plan de pagos. */
    public record CuotaInfo(
            int numeroCuota,
            LocalDate fechaVencimiento,
            BigDecimal valorCapital,
            BigDecimal valorInteres,
            BigDecimal valorCuota,
            BigDecimal saldoCapitalFinal,
            BigDecimal valorPagado,
            BigDecimal saldoPendiente,
            LocalDate fechaPago,
            EstadoVisibleCuota estado,
            int diasMora
    ) {
    }

    /** Plan de pagos completo con totales. */
    public record PlanPagos(
            Long creditoId,
            String numeroCredito,
            LocalDate fechaConsulta,
            int totalCuotas,
            int cuotasPagadas,
            int cuotasVencidas,
            BigDecimal valorVencido,
            List<CuotaInfo> cuotas
    ) {
    }
}
