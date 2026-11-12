package com.portalcreditos.service;

import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCuota;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadoraMoraTest {

    private final CalculadoraMora mora = new CalculadoraMora(new BigDecimal("380"));
    private static final LocalDate HOY = LocalDate.of(2026, 10, 17);

    private static Cuota cuota(int n, LocalDate vence, EstadoCuota estado) {
        Cuota c = new Cuota();
        c.setNumeroCuota(n);
        c.setFechaVencimiento(vence);
        c.setValorCuota(new BigDecimal("849322.00"));
        c.setValorCapital(new BigDecimal("561358.00"));
        c.setValorInteres(new BigDecimal("287964.00"));
        c.setValorPagado(estado == EstadoCuota.PAGADA ? new BigDecimal("849322.00") : BigDecimal.ZERO);
        c.setEstado(estado);
        return c;
    }

    private static List<Cuota> enMora() {
        List<Cuota> l = new ArrayList<>();
        l.add(cuota(14, LocalDate.of(2026, 7, 10), EstadoCuota.PAGADA));
        l.add(cuota(15, LocalDate.of(2026, 8, 10), EstadoCuota.PENDIENTE));
        l.add(cuota(16, LocalDate.of(2026, 9, 10), EstadoCuota.PENDIENTE));
        l.add(cuota(17, LocalDate.of(2026, 10, 10), EstadoCuota.PENDIENTE));
        l.add(cuota(18, LocalDate.of(2026, 11, 10), EstadoCuota.PENDIENTE));
        return l;
    }

    @Test
    @DisplayName("Cobra $380 por cada dia desde la cuota vencida mas antigua (68 dias = $25.840)")
    void cobraPorDiaDesdeLaCuotaMasAntigua() {
        Credito credito = new Credito();
        assertEquals(68, mora.diasPorCobrar(credito, enMora(), HOY));
        assertEquals(0, new BigDecimal("25840.00").compareTo(mora.moraPendiente(credito, enMora(), HOY)));
    }

    @Test
    @DisplayName("Sin cuotas vencidas no hay mora")
    void sinVencidasNoHayMora() {
        Credito credito = new Credito();
        List<Cuota> alDia = List.of(cuota(1, LocalDate.of(2026, 10, 25), EstadoCuota.PENDIENTE));
        assertEquals(0, mora.moraPendiente(credito, alDia, HOY).signum());
    }

    @Test
    @DisplayName("Pagar toda la mora la deja en cero y vuelve a correr desde el dia siguiente")
    void pagoTotalDejaMoraEnCero() {
        Credito credito = new Credito();
        List<Cuota> cuotas = enMora();
        mora.registrarPago(credito, cuotas, new BigDecimal("25840"), HOY);
        assertEquals(0, mora.moraPendiente(credito, cuotas, HOY).signum());
        assertEquals(0, new BigDecimal("380.00").compareTo(mora.moraPendiente(credito, cuotas, HOY.plusDays(1))));
    }

    @Test
    @DisplayName("Un pago parcial de mora descuenta solo los dias completos pagados")
    void pagoParcialDescuentaDiasCompletos() {
        Credito credito = new Credito();
        List<Cuota> cuotas = enMora();
        mora.registrarPago(credito, cuotas, new BigDecimal("3900"), HOY); // 10 dias completos
        assertEquals(58, mora.diasPorCobrar(credito, cuotas, HOY));
    }
}
