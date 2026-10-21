package com.portalcreditos.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula la tabla de amortizacion con el SISTEMA FRANCES (cuota fija).
 *
 * Formula de la cuota:  C = P * i * (1 + i)^n / ((1 + i)^n - 1)
 *   P = valor financiado, i = tasa mensual (en decimal), n = plazo en meses.
 *
 * En cada cuota: interes = saldo * i ; capital = cuota - interes ; nuevo saldo = saldo - capital.
 * Los valores se redondean a pesos; la ultima cuota ajusta la diferencia de redondeo
 * para que el saldo termine exactamente en cero.
 *
 * Es una clase sin dependencias (no consulta la base de datos), por eso se prueba
 * facilmente con pruebas unitarias.
 */
@Service
public class AmortizacionService {

    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Una fila de la tabla de amortizacion. */
    public record FilaAmortizacion(
            int numeroCuota,
            LocalDate fechaVencimiento,
            BigDecimal valorCapital,
            BigDecimal valorInteres,
            BigDecimal valorCuota,
            BigDecimal saldoCapitalFinal
    ) {
    }

    /** Resultado completo del calculo. */
    public record PlanAmortizacion(
            BigDecimal valorCuota,
            BigDecimal totalIntereses,
            BigDecimal totalPagar,
            List<FilaAmortizacion> filas
    ) {
    }

    /**
     * @param valorFinanciado   monto prestado (mayor que cero)
     * @param tasaMensualPct    tasa mensual en porcentaje, ej. 1.45 significa 1.45 %
     * @param plazoMeses        numero de cuotas (1 a 120)
     * @param fechaPrimeraCuota vencimiento de la primera cuota; las demas son mensuales
     */
    public PlanAmortizacion calcular(BigDecimal valorFinanciado, BigDecimal tasaMensualPct,
                                     int plazoMeses, LocalDate fechaPrimeraCuota) {
        validar(valorFinanciado, tasaMensualPct, plazoMeses, fechaPrimeraCuota);

        BigDecimal monto = valorFinanciado.setScale(0, RoundingMode.HALF_UP);
        BigDecimal i = tasaMensualPct.divide(CIEN, PRECISION);
        BigDecimal cuota = calcularCuotaFija(monto, i, plazoMeses);

        List<FilaAmortizacion> filas = new ArrayList<>(plazoMeses);
        BigDecimal saldo = monto;
        BigDecimal totalIntereses = BigDecimal.ZERO;
        BigDecimal totalPagar = BigDecimal.ZERO;

        for (int n = 1; n <= plazoMeses; n++) {
            BigDecimal interes = saldo.multiply(i, PRECISION).setScale(0, RoundingMode.HALF_UP);
            BigDecimal capital;
            BigDecimal valorCuota;
            if (n == plazoMeses) {
                capital = saldo;                    // la ultima cuota liquida todo el saldo
                valorCuota = capital.add(interes);
            } else {
                capital = cuota.subtract(interes);
                valorCuota = cuota;
            }
            saldo = saldo.subtract(capital);
            totalIntereses = totalIntereses.add(interes);
            totalPagar = totalPagar.add(valorCuota);
            filas.add(new FilaAmortizacion(n, fechaPrimeraCuota.plusMonths(n - 1L),
                    dinero(capital), dinero(interes), dinero(valorCuota), dinero(saldo)));
        }
        return new PlanAmortizacion(dinero(cuota), dinero(totalIntereses), dinero(totalPagar), List.copyOf(filas));
    }

    /** Cuota fija redondeada a pesos. Con tasa cero, la cuota es el monto dividido entre el plazo. */
    BigDecimal calcularCuotaFija(BigDecimal monto, BigDecimal i, int n) {
        if (i.signum() == 0) {
            return monto.divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(i).pow(n, PRECISION);
        return monto.multiply(i, PRECISION)
                .multiply(factor, PRECISION)
                .divide(factor.subtract(BigDecimal.ONE), PRECISION)
                .setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validar(BigDecimal monto, BigDecimal tasa, int plazo, LocalDate fecha) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El valor financiado debe ser mayor que cero");
        }
        if (tasa == null || tasa.signum() < 0 || tasa.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalArgumentException("La tasa mensual debe estar entre 0 % y 10 %");
        }
        if (plazo < 1 || plazo > 120) {
            throw new IllegalArgumentException("El plazo debe estar entre 1 y 120 meses");
        }
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha de la primera cuota es obligatoria");
        }
    }
}
