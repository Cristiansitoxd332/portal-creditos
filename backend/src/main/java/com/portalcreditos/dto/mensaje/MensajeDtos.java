package com.portalcreditos.dto.mensaje;

import com.portalcreditos.entity.enums.TipoMensaje;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs de los mensajes a clientes (recordatorios de pago y avisos de mora). */
public final class MensajeDtos {

    private MensajeDtos() {
    }

    /** Credito que necesita un aviso HOY, con el texto sugerido ya armado. */
    public record AvisoSugerido(
            Long creditoId,
            String numeroCredito,
            Long clienteId,
            String cliente,
            String documentoCliente,
            String telefono,
            String placa,
            TipoMensaje tipo,
            LocalDate fechaCuota,
            int numeroCuota,
            BigDecimal valor,
            int diasMora,
            BigDecimal valorMora,
            String titulo,
            String texto,
            /** Enlace de WhatsApp con el texto listo (el administrador lo abre y presiona Enviar). */
            String enlaceWhatsapp,
            boolean enviadoHoy,
            /** Codeudor (fiador) del credito, si tiene: se le puede avisar por WhatsApp cuando hay mora. */
            String codeudor,
            String telefonoCodeudor,
            String enlaceWhatsappCodeudor
    ) {
    }

    /** Pantalla "Mensajes" del administrador: avisos de hoy agrupados. */
    public record PanelAvisos(
            LocalDate fecha,
            boolean esDiaDeCorte,
            LocalDate proximoCorte,
            List<AvisoSugerido> venceHoy,
            List<AvisoSugerido> proximos,
            List<AvisoSugerido> mora
    ) {
    }

    public record EnviarRequest(
            @NotNull(message = "El credito es obligatorio") Long creditoId,
            @NotNull(message = "El tipo de mensaje es obligatorio") TipoMensaje tipo,
            @NotBlank(message = "El texto es obligatorio") @Size(max = 700, message = "Maximo 700 caracteres") String texto
    ) {
    }

    public record EnviarTodosRequest(@NotNull(message = "El tipo de mensaje es obligatorio") TipoMensaje tipo) {
    }

    public record ResultadoEnvio(int enviados, int omitidos, String mensaje) {
    }

    public record MensajeInfo(
            Long id,
            TipoMensaje tipo,
            String titulo,
            String texto,
            LocalDateTime fechaEnvio,
            boolean leido,
            LocalDateTime fechaLectura,
            String numeroCredito,
            String cliente,
            String documentoCliente
    ) {
    }

    public record BandejaCliente(long noLeidos, List<MensajeInfo> mensajes) {
    }
}
