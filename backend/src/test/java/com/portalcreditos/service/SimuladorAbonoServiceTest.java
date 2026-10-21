package com.portalcreditos.service;

import com.portalcreditos.dto.simulador.SimuladorDtos.SimulacionAbono;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCuota;
import com.portalcreditos.exception.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pruebas del simulador de abono a capital sobre el credito CV-2025-0001 (12 de 36 cuotas pagadas). */
class SimuladorAbonoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 17);
    private final AmortizacionService amortizacion = new AmortizacionService();
    private final SimuladorAbonoService simulador =
            new SimuladorAbonoService(null, amortizacion, new DistribuidorPagos(), null);

    private Credito credito() {
        Credito c = new Credito();
        c.setNumeroCredito("CV-2025-0001");
        c.setValorFinanciado(new BigDecimal("60000000.00"));
        c.setTasaInteresMensual(new BigDecimal("1.4500"));
        List<Cuota> cuotas = new ArrayList<>();
        for (AmortizacionService.FilaAmortizacion f : amortizacion.calcular(BigDecimal.valueOf(60_000_000),
                new BigDecimal("1.45"), 36, LocalDate.of(2025, 10, 25)).filas()) {
            Cuota q = new Cuota();
            q.setNumeroCuota(f.numeroCuota());
            q.setFechaVencimiento(f.fechaVencimiento());
            q.setValorCapital(f.valorCapital());
            q.setValorInteres(f.valorInteres());
            q.setValorCuota(f.valorCuota());
            q.setSaldoCapitalFinal(f.saldoCapitalFinal());
            boolean pagada = f.numeroCuota() <= 12;
            q.setValorPagado(pagada ? f.valorCuota() : new BigDecimal("0.00"));
            q.setEstado(pagada ? EstadoCuota.PAGADA : EstadoCuota.PENDIENTE);
            cuotas.add(q);
        }
        c.setCuotas(cuotas);
        return c;
    }

    @Test
    @DisplayName("Sin abono: 24 cuotas restantes sobre el saldo actual de $43.339.201")
    void escenarioBase() {
        SimulacionAbono s = simulador.simular(credito(), new BigDecimal("5000000"), HOY);
        assertEquals(new BigDecimal("43339201.00"), s.saldoCapitalActual());
        assertEquals(24, s.sinAbono().cuotasRestantes());
    }

    @Test
    @DisplayName("Reducir plazo mantiene la cuota, quita cuotas y ahorra intereses")
    void reducirPlazo() {
        SimulacionAbono s = simulador.simular(credito(), new BigDecimal("5000000"), HOY);
        assertEquals(s.sinAbono().valorCuota(), s.reducirPlazo().valorCuota());
        assertEquals(true, s.reducirPlazo().cuotasMenos() > 0);
        assertEquals(true, s.reducirPlazo().ahorroIntereses().signum() > 0);
    }

    @Test
    @DisplayName("Reducir cuota mantiene el numero de cuotas y baja el valor de la cuota")
    void reducirCuota() {
        SimulacionAbono s = simulador.simular(credito(), new BigDecimal("5000000"), HOY);
        assertEquals(24, s.reducirCuota().cuotasRestantes());
        assertEquals(true, s.reducirCuota().valorCuota().compareTo(s.sinAbono().valorCuota()) < 0);
        assertEquals(true, s.reducirCuota().ahorroIntereses().signum() > 0);
    }

    @Test
    @DisplayName("Reducir plazo ahorra mas intereses que reducir cuota")
    void plazoAhorraMas() {
        SimulacionAbono s = simulador.simular(credito(), new BigDecimal("5000000"), HOY);
        assertEquals(true, s.reducirPlazo().ahorroIntereses().compareTo(s.reducirCuota().ahorroIntereses()) > 0);
    }

    @Test
    @DisplayName("Abonar todo el saldo deja cero cuotas y ahorra todos los intereses")
    void abonoTotal() {
        SimulacionAbono s = simulador.simular(credito(), new BigDecimal("43339201"), HOY);
        assertEquals(0, s.reducirPlazo().cuotasRestantes());
        assertEquals(s.sinAbono().totalIntereses(), s.reducirPlazo().ahorroIntereses());
    }

    @Test
    @DisplayName("Rechaza un abono mayor al saldo de capital")
    void rechazaAbonoExcesivo() {
        assertThrows(NegocioException.class, () -> simulador.simular(credito(), new BigDecimal("50000000"), HOY));
    }
}
