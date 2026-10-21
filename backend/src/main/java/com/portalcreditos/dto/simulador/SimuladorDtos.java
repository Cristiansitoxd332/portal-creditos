package com.portalcreditos.dto.simulador;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTO del simulador de abono extraordinario a capital. */
public final class SimuladorDtos {

    private SimuladorDtos() {
    }

    public record SimulacionRequest(
            @NotNull(message = "El valor del abono es obligatorio")
            @DecimalMin(value = "1", message = "El abono debe ser mayor que cero")
            @Digits(integer = 13, fraction = 2, message = "El valor no tiene un formato valido")
            BigDecimal valorAbono
    ) {
    }

    /** Resultado de una modalidad (reducir plazo o reducir cuota). */
    public record Escenario(
            BigDecimal valorCuota,
            int cuotasRestantes,
            BigDecimal totalIntereses,
            LocalDate fechaUltimaCuota,
            BigDecimal ahorroIntereses,
            int cuotasMenos
    ) {
    }

    /** Comparacion entre seguir igual, reducir plazo y reducir cuota. */
    public record SimulacionAbono(
            String numeroCredito,
            BigDecimal saldoCapitalActual,
            BigDecimal valorAbono,
            BigDecimal nuevoSaldoCapital,
            BigDecimal tasaInteresMensual,
            Escenario sinAbono,
            Escenario reducirPlazo,
            Escenario reducirCuota,
            String recomendacion,
            String aviso
    ) {
    }
}
