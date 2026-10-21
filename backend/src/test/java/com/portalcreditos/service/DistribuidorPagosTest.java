package com.portalcreditos.service;

import com.portalcreditos.entity.Cuota;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas unitarias de la aplicacion de pagos a cuotas.
 * Escenario base: credito CV-2025-0002 (cliente en mora), cuota fija $2.100.403,
 * cuotas 1 a 14 pagadas y 15, 16 y 17 vencidas.
 */
class DistribuidorPagosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 17);
    private static final BigDecimal FINANCIADO = new BigDecimal("70000000.00");

    private final DistribuidorPagos distribuidor = new DistribuidorPagos();

    private List<Cuota> cuotasCreditoEnMora() {
        List<Cuota> cuotas = new ArrayList<>();
        for (FilaAmortizacion f : new AmortizacionService().calcular(BigDecimal.valueOf(70_000_000),
                new BigDecimal("1.60"), 48, LocalDate.of(2025, 6, 10)).filas()) {
            Cuota c = new Cuota();
            c.setNumeroCuota(f.numeroCuota());
            c.setFechaVencimiento(f.fechaVencimiento());
            c.setValorCapital(f.valorCapital());
            c.setValorInteres(f.valorInteres());
            c.setValorCuota(f.valorCuota());
            c.setSaldoCapitalFinal(f.saldoCapitalFinal());
            boolean pagada = f.numeroCuota() <= 14;
            c.setValorPagado(pagada ? f.valorCuota() : new BigDecimal("0.00"));
            c.setEstado(pagada ? EstadoCuota.PAGADA : EstadoCuota.PENDIENTE);
            cuotas.add(c);
        }
        return cuotas;
    }

    @Test
    @DisplayName("Pagar exactamente lo vencido deja las 3 cuotas vencidas como PAGADAS")
    void pagoDeLoVencido() {
        List<Cuota> cuotas = cuotasCreditoEnMora();
        List<DistribuidorPagos.Aplicacion> apl = distribuidor.distribuir(cuotas, new BigDecimal("6301209.00"), HOY);

        assertEquals(3, apl.size());
        assertEquals(EstadoCuota.PAGADA, cuotas.get(14).getEstado());
        assertEquals(EstadoCuota.PAGADA, cuotas.get(15).getEstado());
        assertEquals(EstadoCuota.PAGADA, cuotas.get(16).getEstado());
        assertEquals(EstadoCuota.PENDIENTE, cuotas.get(17).getEstado());
        assertEquals(HOY, cuotas.get(16).getFechaPago());
    }

    @Test
    @DisplayName("Un pago se aplica primero a la cuota mas antigua")
    void seAplicaALaMasAntigua() {
        List<Cuota> cuotas = cuotasCreditoEnMora();
        List<DistribuidorPagos.Aplicacion> apl = distribuidor.distribuir(cuotas, new BigDecimal("500000.00"), HOY);

        assertEquals(1, apl.size());
        assertEquals(15, apl.get(0).cuota().getNumeroCuota().intValue());
    }

    @Test
    @DisplayName("Un abono menor a la cuota la deja en estado PARCIAL, sin fecha de pago")
    void abonoParcial() {
        List<Cuota> cuotas = cuotasCreditoEnMora();
        distribuidor.distribuir(cuotas, new BigDecimal("1000000.00"), HOY);
        Cuota c15 = cuotas.get(14);

        assertEquals(EstadoCuota.PARCIAL, c15.getEstado());
        assertEquals(new BigDecimal("1000000.00"), c15.getValorPagado());
        assertEquals(new BigDecimal("1100403.00"), c15.getSaldoPendiente());
        assertNull(c15.getFechaPago());
    }

    @Test
    @DisplayName("Un pago de cuota y media paga una cuota y abona el resto a la siguiente")
    void pagoQueCubreVariasCuotas() {
        List<Cuota> cuotas = cuotasCreditoEnMora();
        List<DistribuidorPagos.Aplicacion> apl = distribuidor.distribuir(cuotas, new BigDecimal("3150604.00"), HOY);

        assertEquals(2, apl.size());
        assertEquals(new BigDecimal("2100403.00"), apl.get(0).valorAplicado());
        assertEquals(new BigDecimal("1050201.00"), apl.get(1).valorAplicado());
        assertEquals(EstadoCuota.PAGADA, cuotas.get(14).getEstado());
        assertEquals(EstadoCuota.PARCIAL, cuotas.get(15).getEstado());
    }

    @Test
    @DisplayName("El saldo de capital baja exactamente en el capital de las cuotas pagadas")
    void saldoCapitalSeActualiza() {
        List<Cuota> cuotas = cuotasCreditoEnMora();
        BigDecimal antes = distribuidor.saldoCapital(FINANCIADO, cuotas);
        BigDecimal capitalCuota15 = cuotas.get(14).getValorCapital();

        distribuidor.distribuir(cuotas, cuotas.get(14).getValorCuota(), HOY);

        assertEquals(new BigDecimal("54751382.00"), antes);  // saldo de los datos ficticios
        assertEquals(antes.subtract(capitalCuota15), distribuidor.saldoCapital(FINANCIADO, cuotas));
    }

    @Test
    @DisplayName("Un abono parcial cubre primero el interes de la cuota y no reduce capital hasta cubrirlo")
    void abonoCubrePrimeroInteres() {
        List<Cuota> cuotas = cuotasCreditoEnMora();
        BigDecimal antes = distribuidor.saldoCapital(FINANCIADO, cuotas);
        BigDecimal interes15 = cuotas.get(14).getValorInteres();

        distribuidor.distribuir(cuotas, interes15, HOY);   // paga solo el interes de la cuota 15

        assertEquals(antes, distribuidor.saldoCapital(FINANCIADO, cuotas));
    }

    @Test
    @DisplayName("Pagar todo lo pendiente deja todas las cuotas pagadas y saldo de capital en cero")
    void pagoTotalCancelaElCredito() {
        List<Cuota> cuotas = cuotasCreditoEnMora();
        distribuidor.distribuir(cuotas, distribuidor.totalPendiente(cuotas), HOY);

        assertEquals(0L, cuotas.stream().filter(c -> c.getEstado() != EstadoCuota.PAGADA).count());
        assertEquals(new BigDecimal("0.00"), distribuidor.saldoCapital(FINANCIADO, cuotas));
    }

    @Test
    @DisplayName("Rechaza pagos en cero, negativos o mayores a lo pendiente")
    void rechazaValoresInvalidos() {
        List<Cuota> cuotas = cuotasCreditoEnMora();
        BigDecimal excedido = distribuidor.totalPendiente(cuotas).add(BigDecimal.ONE);

        assertThrows(IllegalArgumentException.class, () -> distribuidor.distribuir(cuotas, BigDecimal.ZERO, HOY));
        assertThrows(IllegalArgumentException.class, () -> distribuidor.distribuir(cuotas, new BigDecimal("-1"), HOY));
        assertThrows(IllegalArgumentException.class, () -> distribuidor.distribuir(cuotas, excedido, HOY));
    }
}
