package com.portalcreditos.dto.abono;

import com.portalcreditos.dto.credito.CreditoDtos.CodeudorInfo;
import com.portalcreditos.dto.credito.CreditoDtos.CreditoResumen;
import com.portalcreditos.entity.enums.MedioPago;
import com.portalcreditos.entity.enums.TipoAbono;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs de "Abonos individuales" e "Historial de creditos" del administrador. */
public final class AbonoDtos {

    private AbonoDtos() {
    }

    /** Abono que registra el administrador. Para TOTAL el valor puede ir vacio: se usa el valor de cancelacion. */
    public record AbonoRequest(
            @NotNull(message = "El credito es obligatorio") Long creditoId,
            @NotNull(message = "El tipo de abono es obligatorio") TipoAbono tipoAbono,
            @DecimalMin(value = "1", message = "El valor debe ser mayor que cero") @Digits(integer = 13, fraction = 2) BigDecimal valor,
            @NotNull(message = "La forma de pago es obligatoria") MedioPago medioPago,
            @Size(max = 200, message = "El concepto no puede superar 200 caracteres") String concepto,
            /** Sucursal donde se recibe el pago ("01" a "14"). Vacio = la sucursal del credito. */
            String sucursal
    ) {
    }

    /** Credito activo del deudor con los valores sugeridos para abonar hoy. */
    public record CreditoParaAbono(
            CreditoResumen credito,
            BigDecimal valorNormalPago,
            BigDecimal valorTotalPago,
            BigDecimal seguroVidaCuota,
            BigDecimal fogadeCuota,
            String sucursal
    ) {
    }

    /** Sucursal de Rentar. */
    public record SucursalInfo(String codigo, String nombre) {
    }

    /** Deudor encontrado por identificacion, con sus creditos activos. */
    public record DeudorAbonos(
            Long clienteId,
            String documento,
            String nombre,
            String telefono,
            LocalDate fecha,
            List<CreditoParaAbono> creditos
    ) {
    }

    public record LineaAbono(String tipo, Integer cuota, String detalle, BigDecimal saldoAnterior, BigDecimal valor, BigDecimal saldo) {
    }

    /** Vista previa: como quedaria distribuido el abono (no guarda nada). */
    public record VistaPreviaAbono(
            Long creditoId,
            String numeroCredito,
            TipoAbono tipoAbono,
            BigDecimal valor,
            BigDecimal aMora,
            BigDecimal aCuotas,
            BigDecimal aCapital,
            BigDecimal saldoCapitalAnterior,
            BigDecimal saldoCapitalNuevo,
            int cuotasPendientesAntes,
            int cuotasPendientesDespues,
            BigDecimal valorCuotaAntes,
            BigDecimal valorCuotaDespues,
            boolean cancelaCredito,
            String resumen,
            List<LineaAbono> lineas
    ) {
    }

    /** Valores de un credito calculados a una fecha (boton "Calc. corte" o "Calc. total"). */
    public record CalculoFecha(
            LocalDate fecha,
            boolean total,
            BigDecimal capital,
            BigDecimal interes,
            BigDecimal seguroVida,
            BigDecimal fogade,
            BigDecimal mora,
            int diasMora,
            int diasCausados,
            BigDecimal valorPagar,
            String explicacion
    ) {
    }

    /** Persona consultada en el historial (puede ser deudor, codeudor o ambos). */
    public record PersonaHistorial(
            String documento,
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String ciudad,
            boolean esCliente,
            boolean esCodeudor
    ) {
    }

    /** Un credito en el historial, con su codeudor y datos de la operacion. */
    public record CreditoHistorial(
            CreditoResumen credito,
            String tipoCredito,
            String periodicidad,
            LocalDate fechaDesembolso,
            LocalDate fechaCancelacion,
            String asesor,
            String sucursal,
            BigDecimal cuotaOficial,
            BigDecimal seguroVidaCuota,
            BigDecimal fogadeCuota,
            CodeudorInfo codeudor
    ) {
    }

    public record Historial(
            PersonaHistorial persona,
            LocalDate fecha,
            List<CreditoHistorial> creditosDeudor,
            List<CreditoHistorial> creditosCodeudor
    ) {
    }
}
