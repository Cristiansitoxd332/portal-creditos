package com.portalcreditos.service;

import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCuota;
import com.portalcreditos.entity.enums.TipoAbono;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de abonos: seguros, abono normal, abono a capital (bajar tiempo / bajar cuota) y total pago. */
class MotorAbonosTest {

    private static final BigDecimal TASA = new BigDecimal("2.84");
    private static final LocalDate DESEMBOLSO = LocalDate.of(2026, 1, 1);
    private final AmortizacionService amortizacion = new AmortizacionService();
    private final MotorAbonos motor = new MotorAbonos(new DistribuidorPagos());

    /** Credito de $10.000.000 a 12 meses con seguros de $20.000 + $20.000 por cuota. */
    private List<Cuota> plan() {
        var p = amortizacion.calcular(new BigDecimal("10000000"), TASA, 12, LocalDate.of(2026, 2, 5));
        List<Cuota> cuotas = new ArrayList<>();
        for (var f : p.filas()) {
            Cuota q = new Cuota();
            q.setNumeroCuota(f.numeroCuota());
            q.setFechaVencimiento(f.fechaVencimiento());
            q.setValorCapital(f.valorCapital());
            q.setValorInteres(f.valorInteres());
            q.setValorSeguroVida(new BigDecimal("20000.00"));
            q.setValorFogade(new BigDecimal("20000.00"));
            q.setValorCuota(f.valorCuota().add(new BigDecimal("40000.00")));
            q.setSaldoCapitalFinal(f.saldoCapitalFinal());
            q.setValorPagado(BigDecimal.ZERO.setScale(2));
            q.setEstado(EstadoCuota.PENDIENTE);
            cuotas.add(q);
        }
        return cuotas;
    }

    @Test
    void divideLaDiferenciaEntreSeguroDeVidaYFogade() {
        var s = CreditoFactory.dividirSeguros(new BigDecimal("1000000"), new BigDecimal("1091379"));
        assertEquals(new BigDecimal("45690.00"), s.seguroVida());
        assertEquals(new BigDecimal("45689.00"), s.fogade());
        assertEquals(new BigDecimal("91379.00"), s.total());
        assertEquals(BigDecimal.ZERO.setScale(2), CreditoFactory.dividirSeguros(new BigDecimal("1000"), null).total());
    }

    @Test
    void dentroDeLaCuotaSePaganPrimeroLosSeguros() {
        List<Cuota> cuotas = plan();
        var r = motor.aplicar(TipoAbono.NORMAL, new BigDecimal("30000"), cuotas, TASA, DESEMBOLSO,
                BigDecimal.ZERO, 0, new BigDecimal("380"), LocalDate.of(2026, 2, 1));
        assertEquals("SEGURO_VIDA", r.lineas().get(0).tipo());
        assertEquals("FOGADE", r.lineas().get(1).tipo());
        assertEquals(BigDecimal.ZERO.setScale(2), cuotas.get(0).getCapitalCubierto().setScale(2));
    }

    @Test
    void abonoACapitalBajandoTiempoReduceElNumeroDeCuotas() {
        List<Cuota> cuotas = plan();
        LocalDate hoy = LocalDate.of(2026, 2, 1);
        var r = motor.aplicar(TipoAbono.CAPITAL_PLAZO, new BigDecimal("3000000"), cuotas, TASA, DESEMBOLSO,
                BigDecimal.ZERO, 0, new BigDecimal("380"), hoy);
        cuotas.removeAll(r.eliminadas());
        assertTrue(cuotas.size() < 12);
        BigDecimal capital = cuotas.stream().map(Cuota::getValorCapital).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("7000000.00"), capital.setScale(2));
    }

    @Test
    void abonoACapitalBajandoCuotaMantieneElPlazo() {
        List<Cuota> cuotas = plan();
        BigDecimal antes = cuotas.get(0).getValorCuota();
        var r = motor.aplicar(TipoAbono.CAPITAL_CUOTA, new BigDecimal("3000000"), cuotas, TASA, DESEMBOLSO,
                BigDecimal.ZERO, 0, new BigDecimal("380"), LocalDate.of(2026, 2, 1));
        assertEquals(0, r.eliminadas().size());
        assertTrue(r.nuevaCuota().compareTo(antes) < 0);
        assertEquals(new BigDecimal("40000.00"), cuotas.get(0).getValorSeguros());
    }

    @Test
    void totalPagoCancelaTodo() {
        List<Cuota> cuotas = plan();
        LocalDate hoy = LocalDate.of(2026, 3, 20);
        var liq = motor.liquidacion(cuotas, DESEMBOLSO, new BigDecimal("5700"), 15, hoy);
        var r = motor.aplicar(TipoAbono.TOTAL, liq.total(), cuotas, TASA, DESEMBOLSO, new BigDecimal("5700"), 15,
                new BigDecimal("380"), hoy);
        cuotas.removeAll(r.eliminadas());
        assertTrue(cuotas.stream().allMatch(q -> q.getEstado() == EstadoCuota.PAGADA));
        assertEquals(BigDecimal.ZERO.setScale(2), new DistribuidorPagos().saldoCapital(BigDecimal.ZERO, cuotas).setScale(2));
    }
}
