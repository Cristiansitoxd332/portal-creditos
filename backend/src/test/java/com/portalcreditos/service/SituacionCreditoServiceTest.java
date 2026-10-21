package com.portalcreditos.service;

import com.portalcreditos.dto.credito.EstadoVisibleCuota;
import com.portalcreditos.dto.credito.Semaforo;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoCuota;
import com.portalcreditos.service.AmortizacionService.FilaAmortizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pruebas unitarias de la situacion del credito (al dia, en mora, cancelado, proxima a vencer).
 * Reproducen los escenarios de los datos ficticios con fecha de referencia 17/10/2026.
 */
class SituacionCreditoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 17);

    private final AmortizacionService amortizacion = new AmortizacionService();
    private final SituacionCreditoService servicio = new SituacionCreditoService(5);

    /** Construye un credito con su plan de pagos y marca como pagadas las primeras N cuotas. */
    private Credito credito(long monto, String tasa, int plazo, LocalDate primeraCuota, int cuotasPagadas) {
        Credito credito = new Credito();
        credito.setValorFinanciado(BigDecimal.valueOf(monto).setScale(2));
        List<Cuota> cuotas = new ArrayList<>();
        for (FilaAmortizacion f : amortizacion.calcular(BigDecimal.valueOf(monto), new BigDecimal(tasa), plazo, primeraCuota).filas()) {
            Cuota c = new Cuota();
            c.setCredito(credito);
            c.setNumeroCuota(f.numeroCuota());
            c.setFechaVencimiento(f.fechaVencimiento());
            c.setValorCapital(f.valorCapital());
            c.setValorInteres(f.valorInteres());
            c.setValorCuota(f.valorCuota());
            c.setSaldoCapitalFinal(f.saldoCapitalFinal());
            if (f.numeroCuota() <= cuotasPagadas) {
                c.setValorPagado(f.valorCuota());
                c.setFechaPago(f.fechaVencimiento());
                c.setEstado(EstadoCuota.PAGADA);
            } else {
                c.setValorPagado(BigDecimal.ZERO.setScale(2));
                c.setEstado(EstadoCuota.PENDIENTE);
            }
            cuotas.add(c);
        }
        credito.setCuotas(cuotas);
        return credito;
    }

    @Test
    @DisplayName("Credito al dia: sin cuotas vencidas y con la proxima cuota identificada")
    void creditoAlDia() {
        Credito c = credito(60_000_000, "1.45", 36, LocalDate.of(2025, 10, 25), 12);
        SituacionCreditoService.Situacion s = servicio.evaluar(c, c.getCuotas(), HOY);

        assertEquals(EstadoCredito.VIGENTE, s.estado());
        assertEquals(Semaforo.AL_DIA, s.semaforo());
        assertEquals(12, s.cuotasPagadas());
        assertEquals(24, s.cuotasPendientes());
        assertEquals(0, s.cuotasVencidas());
        assertEquals(0, s.diasMora());
        assertEquals(13, s.proximaCuota().getNumeroCuota().intValue());
        assertEquals(8, s.diasParaProximaCuota().intValue());
    }

    @Test
    @DisplayName("Credito en mora: 3 cuotas vencidas, valor vencido y dias de mora desde la mas antigua")
    void creditoEnMora() {
        Credito c = credito(70_000_000, "1.60", 48, LocalDate.of(2025, 6, 10), 14);
        SituacionCreditoService.Situacion s = servicio.evaluar(c, c.getCuotas(), HOY);

        assertEquals(EstadoCredito.EN_MORA, s.estado());
        assertEquals(Semaforo.EN_MORA, s.semaforo());
        assertEquals(3, s.cuotasVencidas());                         // ago, sep y oct de 2026
        assertEquals(new BigDecimal("6301209.00"), s.valorVencido()); // 3 x 2.100.403
        assertEquals(68, s.diasMora());                               // desde el 10/08/2026
    }

    @Test
    @DisplayName("Credito cancelado: todas las cuotas pagadas y 100 % de avance")
    void creditoCancelado() {
        Credito c = credito(30_000_000, "1.30", 12, LocalDate.of(2025, 9, 5), 12);
        SituacionCreditoService.Situacion s = servicio.evaluar(c, c.getCuotas(), HOY);

        assertEquals(EstadoCredito.CANCELADO, s.estado());
        assertEquals(Semaforo.CANCELADO, s.semaforo());
        assertEquals(new BigDecimal("100.0"), s.porcentajePagado());
        assertNull(s.proximaCuota());
    }

    @Test
    @DisplayName("Proxima a vencer: al dia, pero la cuota vence en 5 dias o menos")
    void proximaAVencer() {
        Credito c = credito(50_000_000, "1.55", 60, LocalDate.of(2026, 10, 18), 0);
        SituacionCreditoService.Situacion s = servicio.evaluar(c, c.getCuotas(), HOY);

        assertEquals(EstadoCredito.VIGENTE, s.estado());
        assertEquals(Semaforo.PROXIMA_A_VENCER, s.semaforo());
        assertEquals(1, s.diasParaProximaCuota().intValue());
        assertEquals(new BigDecimal("0.0"), s.porcentajePagado());
    }

    @Test
    @DisplayName("Una cuota que vence hoy todavia no esta vencida")
    void cuotaQueVenceHoyNoEstaVencida() {
        Credito c = credito(50_000_000, "1.55", 60, HOY, 0);
        Cuota primera = c.getCuotas().get(0);

        assertEquals(EstadoVisibleCuota.PENDIENTE, servicio.estadoVisible(primera, HOY));
        assertEquals(0, servicio.diasMora(primera, HOY));
        assertEquals(EstadoVisibleCuota.VENCIDA, servicio.estadoVisible(primera, HOY.plusDays(1)));
        assertEquals(1, servicio.diasMora(primera, HOY.plusDays(1)));
    }

    @Test
    @DisplayName("Abono parcial vencido: solo cuenta como mora lo que falta por pagar")
    void abonoParcialVencido() {
        Credito c = credito(60_000_000, "1.45", 36, LocalDate.of(2025, 10, 25), 11);
        Cuota cuota12 = c.getCuotas().get(11);                 // vencio el 25/09/2026
        cuota12.setValorPagado(new BigDecimal("1000000.00"));
        cuota12.setEstado(EstadoCuota.PARCIAL);

        SituacionCreditoService.Situacion s = servicio.evaluar(c, c.getCuotas(), HOY);

        assertEquals(EstadoVisibleCuota.VENCIDA, servicio.estadoVisible(cuota12, HOY));
        assertEquals(1, s.cuotasVencidas());
        assertEquals(new BigDecimal("1151127.00"), s.valorVencido()); // 2.151.127 - 1.000.000
        assertEquals(22, s.diasMora());
    }
}
