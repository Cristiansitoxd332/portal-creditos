package com.portalcreditos.service;

import com.portalcreditos.service.AmortizacionService.FilaAmortizacion;
import com.portalcreditos.service.AmortizacionService.PlanAmortizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas unitarias del calculo de amortizacion (sistema frances).
 * Los valores esperados coinciden con el plan de pagos de los datos ficticios.
 */
class AmortizacionServiceTest {

    private final AmortizacionService servicio = new AmortizacionService();

    private static BigDecimal pesos(long valor) {
        return BigDecimal.valueOf(valor).setScale(2);
    }

    @Test
    @DisplayName("Calcula la cuota fija del credito CV-2025-0001 ($60.000.000 al 1,45 % mensual, 36 meses)")
    void cuotaFijaDeUnCreditoConocido() {
        PlanAmortizacion plan = servicio.calcular(BigDecimal.valueOf(60_000_000), new BigDecimal("1.45"),
                36, LocalDate.of(2025, 10, 25));

        assertEquals(pesos(2_151_127), plan.valorCuota());
        assertEquals(36, plan.filas().size());
    }

    @Test
    @DisplayName("La primera cuota separa correctamente interes y capital")
    void primeraCuotaSeparaInteresYCapital() {
        PlanAmortizacion plan = servicio.calcular(BigDecimal.valueOf(60_000_000), new BigDecimal("1.45"),
                36, LocalDate.of(2025, 10, 25));
        FilaAmortizacion primera = plan.filas().get(0);

        assertEquals(pesos(870_000), primera.valorInteres());       // 60.000.000 x 1,45 %
        assertEquals(pesos(1_281_127), primera.valorCapital());     // cuota - interes
        assertEquals(pesos(58_718_873), primera.saldoCapitalFinal());
    }

    @Test
    @DisplayName("La ultima cuota ajusta el redondeo y el saldo termina exactamente en cero")
    void ultimaCuotaDejaSaldoEnCero() {
        PlanAmortizacion plan = servicio.calcular(BigDecimal.valueOf(60_000_000), new BigDecimal("1.45"),
                36, LocalDate.of(2025, 10, 25));
        FilaAmortizacion ultima = plan.filas().get(35);

        assertEquals(pesos(2_151_138), ultima.valorCuota());
        assertEquals(pesos(0), ultima.saldoCapitalFinal());
    }

    @Test
    @DisplayName("La suma del capital de todas las cuotas es igual al valor financiado")
    void sumaDeCapitalIgualAlMonto() {
        PlanAmortizacion plan = servicio.calcular(BigDecimal.valueOf(70_000_000), new BigDecimal("1.60"),
                48, LocalDate.of(2025, 6, 10));
        BigDecimal capital = plan.filas().stream().map(FilaAmortizacion::valorCapital)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(pesos(70_000_000), capital);
        assertEquals(pesos(2_100_403), plan.valorCuota());
    }

    @Test
    @DisplayName("Total a pagar = valor financiado + total de intereses")
    void totalesConsistentes() {
        PlanAmortizacion plan = servicio.calcular(BigDecimal.valueOf(30_000_000), new BigDecimal("1.30"),
                12, LocalDate.of(2025, 9, 5));

        assertEquals(plan.totalPagar(), pesos(30_000_000).add(plan.totalIntereses()));
    }

    @Test
    @DisplayName("Con tasa cero no hay intereses y la cuota es el monto dividido entre el plazo")
    void tasaCero() {
        PlanAmortizacion plan = servicio.calcular(BigDecimal.valueOf(1_200_000), BigDecimal.ZERO,
                12, LocalDate.of(2026, 1, 15));

        assertEquals(pesos(100_000), plan.valorCuota());
        assertEquals(pesos(0), plan.totalIntereses());
    }

    @Test
    @DisplayName("Un credito a una sola cuota paga capital mas un mes de interes")
    void plazoDeUnMes() {
        PlanAmortizacion plan = servicio.calcular(BigDecimal.valueOf(1_000_000), new BigDecimal("2"),
                1, LocalDate.of(2026, 1, 15));

        assertEquals(pesos(1_020_000), plan.valorCuota());
        assertEquals(pesos(1_000_000), plan.filas().get(0).valorCapital());
    }

    @Test
    @DisplayName("Las fechas son mensuales y respetan los meses cortos")
    void fechasMensualesConFinDeMes() {
        PlanAmortizacion plan = servicio.calcular(BigDecimal.valueOf(3_000_000), new BigDecimal("1.5"),
                3, LocalDate.of(2026, 1, 31));

        assertEquals(LocalDate.of(2026, 1, 31), plan.filas().get(0).fechaVencimiento());
        assertEquals(LocalDate.of(2026, 2, 28), plan.filas().get(1).fechaVencimiento());
        assertEquals(LocalDate.of(2026, 3, 31), plan.filas().get(2).fechaVencimiento());
    }

    @Test
    @DisplayName("Rechaza datos invalidos: monto, tasa y plazo fuera de rango")
    void rechazaDatosInvalidos() {
        LocalDate fecha = LocalDate.of(2026, 1, 15);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.calcular(BigDecimal.ZERO, BigDecimal.ONE, 12, fecha));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.calcular(BigDecimal.TEN, new BigDecimal("-1"), 12, fecha));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.calcular(BigDecimal.TEN, BigDecimal.ONE, 0, fecha));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.calcular(BigDecimal.TEN, BigDecimal.ONE, 121, fecha));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.calcular(BigDecimal.TEN, BigDecimal.ONE, 12, null));
    }
}
