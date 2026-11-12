package com.portalcreditos.dto.admin;

import com.portalcreditos.dto.credito.Semaforo;
import com.portalcreditos.entity.enums.ClaseVehiculo;
import com.portalcreditos.entity.enums.TipoDocumento;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTO del panel administrativo: clientes, creacion de creditos y tablero de metricas. */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ------------------------------------------------------------------ clientes

    public record ClienteRequest(
            @NotNull(message = "El tipo de documento es obligatorio") TipoDocumento tipoDocumento,
            @NotBlank(message = "El numero de documento es obligatorio")
            @Pattern(regexp = "^[0-9A-Za-z]{5,20}$", message = "El documento debe tener entre 5 y 20 letras o numeros")
            String numeroDocumento,
            @NotBlank(message = "Los nombres son obligatorios") @Size(max = 100) String nombres,
            @NotBlank(message = "Los apellidos son obligatorios") @Size(max = 100) String apellidos,
            @Email(message = "El correo no es valido") @Size(max = 150) String correo,
            @Pattern(regexp = "^$|^[0-9+ ()-]{7,20}$", message = "El telefono no es valido") String telefono,
            @Size(max = 200) String direccion,
            @Size(max = 80) String ciudad
    ) {
    }

    public record ClienteInfo(
            Long id,
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            String nombres,
            String apellidos,
            String correo,
            String telefono,
            String direccion,
            String ciudad,
            int creditos,
            boolean usuarioActivo,
            boolean usuarioBloqueado,
            boolean debeCambiarPassword,
            LocalDateTime ultimoAcceso
    ) {
    }

    /** Respuesta al crear un cliente o restablecer su clave: la clave temporal se muestra UNA sola vez. */
    public record CredencialesTemporales(
            Long clienteId,
            String usuario,
            String passwordTemporal,
            String mensaje
    ) {
    }

    // ------------------------------------------------------------------ creditos

    public record CreditoRequest(
            @NotNull(message = "El cliente es obligatorio") Long clienteId,

            @NotBlank(message = "La placa es obligatoria")
            @Pattern(regexp = "^[A-Za-z]{3}[0-9]{2}[0-9A-Za-z]$", message = "La placa debe tener el formato ABC123 o ABC12D")
            String placa,
            @NotBlank(message = "La marca es obligatoria") @Size(max = 50) String marca,
            @NotBlank(message = "La linea es obligatoria") @Size(max = 60) String linea,
            @NotNull(message = "El modelo es obligatorio") @Min(1990) @Max(2100) Integer modelo,
            @Size(max = 30) String color,
            @NotNull(message = "La clase de vehiculo es obligatoria") ClaseVehiculo clase,
            @Size(max = 40) String numeroMotor,
            @Size(max = 40) String numeroChasis,

            @NotNull(message = "El valor del vehiculo es obligatorio")
            @DecimalMin(value = "1000000", message = "El valor del vehiculo debe ser de al menos $1.000.000")
            @Digits(integer = 13, fraction = 2) BigDecimal valorVehiculo,
            @NotNull(message = "La cuota inicial es obligatoria (puede ser 0)")
            @DecimalMin(value = "0") @Digits(integer = 13, fraction = 2) BigDecimal cuotaInicial,
            @NotNull(message = "La tasa es obligatoria")
            @DecimalMin(value = "0") @DecimalMax(value = "10", message = "La tasa mensual no puede superar el 10 %")
            @Digits(integer = 2, fraction = 4) BigDecimal tasaInteresMensual,
            @NotNull(message = "El plazo es obligatorio") @Min(1) @Max(120) Integer plazoMeses,
            @NotNull(message = "La fecha de desembolso es obligatoria") LocalDate fechaDesembolso,
            LocalDate fechaPrimeraCuota,
            /** true = el administrador confirmo que es un credito ADICIONAL (otra moto) para un cliente con credito activo. */
            Boolean creditoAdicional
    ) {
    }

    /** Vista previa del plan antes de crear el credito. */
    public record SimulacionPlanRequest(
            @NotNull @DecimalMin(value = "1") @Digits(integer = 13, fraction = 2) BigDecimal valorFinanciado,
            @NotNull @DecimalMin(value = "0") @DecimalMax(value = "10") @Digits(integer = 2, fraction = 4) BigDecimal tasaInteresMensual,
            @NotNull @Min(1) @Max(120) Integer plazoMeses,
            @NotNull LocalDate fechaPrimeraCuota
    ) {
    }

    // ------------------------------------------------------------------ tablero

    public record PagosMes(String mes, long cantidad, BigDecimal total) {
    }

    public record Tablero(
            LocalDate fechaCorte,
            long totalClientes,
            long totalCreditos,
            long creditosAlDia,
            long creditosEnMora,
            long creditosCancelados,
            BigDecimal carteraTotal,
            BigDecimal carteraVencida,
            BigDecimal indiceMora,
            long pagosMesCantidad,
            BigDecimal pagosMesValor,
            long solicitudesPendientes,
            Map<Semaforo, Long> creditosPorSemaforo,
            List<PagosMes> pagosUltimosMeses,
            BigDecimal recaudoTramites,
            long tramitesPagados,
            String cuentaRecaudoTramites
    ) {
    }
}
