package com.portalcreditos.service;

import com.portalcreditos.dto.credito.CreditoDtos.CreditoResumen;
import com.portalcreditos.dto.mensaje.MensajeDtos.AvisoSugerido;
import com.portalcreditos.dto.mensaje.MensajeDtos.BandejaCliente;
import com.portalcreditos.dto.mensaje.MensajeDtos.EnviarRequest;
import com.portalcreditos.dto.mensaje.MensajeDtos.MensajeInfo;
import com.portalcreditos.dto.mensaje.MensajeDtos.PanelAvisos;
import com.portalcreditos.dto.mensaje.MensajeDtos.ResultadoEnvio;
import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.MensajeCliente;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.TipoMensaje;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.MensajeClienteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recordatorios de pago y avisos de mora para los clientes.
 *
 * Todo se calcula EN TIEMPO REAL con la fecha de hoy: que cuotas vencen hoy (dia de corte 5 o 20),
 * cuales vencen en los proximos dias y que creditos estan en mora. El administrador revisa el texto
 * sugerido y lo envia: el mensaje queda en el portal del cliente (seccion "Mensajes") y, si quiere,
 * puede abrir WhatsApp con el mismo texto listo para enviarlo desde el numero de la entidad.
 * No hay envio automatico de SMS ni integracion con proveedores de mensajeria.
 */
@Service
public class MensajeService {

    /** Dias de anticipacion para el recordatorio "su cuota vence pronto". */
    static final int DIAS_RECORDATORIO = 3;
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_CO);

    private final CreditoRepository creditos;
    private final CreditoConsultaService consulta;
    private final MensajeClienteRepository mensajes;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final String lineaAtencion;

    public MensajeService(CreditoRepository creditos, CreditoConsultaService consulta, MensajeClienteRepository mensajes,
                          AuditoriaService auditoria, Clock reloj,
                          @Value("${app.contacto.linea-atencion:310 206 6474}") String lineaAtencion) {
        this.creditos = creditos;
        this.consulta = consulta;
        this.mensajes = mensajes;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.lineaAtencion = lineaAtencion;
    }

    // ================================================================== administrador

    /** Avisos que corresponden HOY, agrupados: vence hoy, vence pronto y en mora. */
    @Transactional(readOnly = true)
    public PanelAvisos panel() {
        LocalDate hoy = LocalDate.now(reloj);
        List<AvisoSugerido> todos = sugeridos(hoy);
        return new PanelAvisos(hoy, CreditoFactory.esFechaDeCorte(hoy),
                CreditoFactory.siguienteCorte(hoy.plusDays(1)),
                filtrar(todos, TipoMensaje.VENCE_HOY), filtrar(todos, TipoMensaje.RECORDATORIO), filtrar(todos, TipoMensaje.MORA));
    }

    @Transactional
    public MensajeInfo enviar(EnviarRequest r, String admin, String ip) {
        Credito credito = creditos.findById(r.creditoId())
                .orElseThrow(() -> NegocioException.noEncontrado("El credito no existe"));
        MensajeCliente m = guardar(credito, r.tipo(), r.texto().trim(), admin);
        auditoria.registrar(admin, "ENVIAR_MENSAJE", "mensaje_cliente", String.valueOf(m.getId()),
                r.tipo() + " a " + credito.getCliente().getNumeroDocumento() + " - " + credito.getNumeroCredito(), ip);
        return info(m);
    }

    /** Envia el texto sugerido a todos los creditos de un grupo que aun no recibieron ese aviso hoy. */
    @Transactional
    public ResultadoEnvio enviarTodos(TipoMensaje tipo, String admin, String ip) {
        if (tipo == TipoMensaje.GENERAL) {
            throw NegocioException.solicitudInvalida("El envio masivo es solo para recordatorios y avisos de mora");
        }
        LocalDate hoy = LocalDate.now(reloj);
        int enviados = 0;
        int omitidos = 0;
        for (AvisoSugerido a : filtrar(sugeridos(hoy), tipo)) {
            if (a.enviadoHoy()) {
                omitidos++;
                continue;
            }
            guardar(creditos.findById(a.creditoId()).orElseThrow(), tipo, a.texto(), admin);
            enviados++;
        }
        auditoria.registrar(admin, "ENVIAR_MENSAJES_MASIVO", "mensaje_cliente", null,
                tipo + ": " + enviados + " enviados, " + omitidos + " ya tenian el aviso de hoy", ip);
        String texto = enviados == 0
                ? "No habia mensajes pendientes: todos los clientes de este grupo ya recibieron el aviso de hoy."
                : "Se enviaron " + enviados + " mensaje(s)" + (omitidos > 0 ? "; " + omitidos + " ya lo tenian hoy." : ".");
        return new ResultadoEnvio(enviados, omitidos, texto);
    }

    /** Ultimos mensajes enviados (bitacora del administrador). */
    @Transactional(readOnly = true)
    public List<MensajeInfo> historial() {
        return mensajes.findTop200ByOrderByFechaEnvioDesc().stream().map(this::info).toList();
    }

    // ================================================================== cliente

    @Transactional(readOnly = true)
    public BandejaCliente bandeja(Long clienteId) {
        List<MensajeInfo> lista = mensajes.findByClienteIdOrderByFechaEnvioDesc(clienteId).stream().map(this::info).toList();
        return new BandejaCliente(lista.stream().filter(m -> !m.leido()).count(), lista);
    }

    @Transactional(readOnly = true)
    public long noLeidos(Long clienteId) {
        return mensajes.countByClienteIdAndLeidoFalse(clienteId);
    }

    @Transactional
    public MensajeInfo marcarLeido(Long clienteId, Long mensajeId) {
        MensajeCliente m = mensajes.findByIdAndClienteId(mensajeId, clienteId)
                .orElseThrow(() -> NegocioException.noEncontrado("El mensaje no existe"));
        if (!m.isLeido()) {
            m.setLeido(true);
            m.setFechaLectura(LocalDateTime.now(reloj));
        }
        return info(m);
    }

    // ================================================================== calculo de avisos

    private List<AvisoSugerido> sugeridos(LocalDate hoy) {
        Map<Long, Credito> porId = creditos.findAll().stream()
                .filter(c -> c.getEstado() != EstadoCredito.CANCELADO)
                .collect(Collectors.toMap(Credito::getId, Function.identity()));
        LocalDateTime inicioDia = hoy.atStartOfDay();
        List<AvisoSugerido> lista = new ArrayList<>();
        for (CreditoResumen r : consulta.listarTodos()) {
            Credito c = porId.get(r.id());
            if (c == null) {
                continue;
            }
            TipoMensaje tipo = clasificar(r, hoy);
            if (tipo == null) {
                continue;
            }
            String texto = textoSugerido(tipo, c.getCliente(), r, hoy);
            boolean enviadoHoy = mensajes.existsByCreditoIdAndTipoAndFechaEnvioGreaterThanEqual(c.getId(), tipo, inicioDia);
            LocalDate fechaCuota = r.proximaCuota() == null ? null : r.proximaCuota().fechaVencimiento();
            lista.add(new AvisoSugerido(c.getId(), r.numeroCredito(), c.getCliente().getId(), r.cliente(), r.documentoCliente(),
                    c.getCliente().getTelefono(), r.placa(), tipo, fechaCuota,
                    r.proximaCuota() == null ? 0 : r.proximaCuota().numeroCuota(),
                    tipo == TipoMensaje.MORA ? r.valorVencido() : (r.proximaCuota() == null ? BigDecimal.ZERO : r.proximaCuota().valor()),
                    r.diasMora(), r.valorMora(), titulo(tipo), texto, enlaceWhatsapp(c.getCliente().getTelefono(), texto), enviadoHoy,
                    c.getCodeudor() == null ? null : c.getCodeudor().getNombreCompleto(),
                    c.getCodeudor() == null ? null : c.getCodeudor().getTelefono(),
                    c.getCodeudor() == null || tipo != TipoMensaje.MORA ? null
                            : enlaceWhatsapp(c.getCodeudor().getTelefono(), textoCodeudor(c, r))));
        }
        lista.sort(Comparator.comparing(AvisoSugerido::diasMora).reversed().thenComparing(AvisoSugerido::cliente));
        return lista;
    }

    /** MORA si tiene cuotas vencidas; VENCE_HOY si la proxima cuota vence hoy; RECORDATORIO si vence en 1 a 3 dias. */
    static TipoMensaje clasificar(CreditoResumen r, LocalDate hoy) {
        if (r.cuotasVencidas() > 0) {
            return TipoMensaje.MORA;
        }
        if (r.proximaCuota() == null) {
            return null;
        }
        long dias = ChronoUnit.DAYS.between(hoy, r.proximaCuota().fechaVencimiento());
        if (dias == 0) {
            return TipoMensaje.VENCE_HOY;
        }
        return dias > 0 && dias <= DIAS_RECORDATORIO ? TipoMensaje.RECORDATORIO : null;
    }

    static String titulo(TipoMensaje tipo) {
        return switch (tipo) {
            case VENCE_HOY -> "Hoy vence su cuota";
            case RECORDATORIO -> "Su cuota vence pronto";
            case MORA -> "Su crédito se encuentra en mora";
            case GENERAL -> "Mensaje de Rentar Inversiones";
        };
    }

    String textoSugerido(TipoMensaje tipo, Cliente cliente, CreditoResumen r, LocalDate hoy) {
        String nombre = primerNombre(cliente.getNombres());
        String credito = r.numeroCredito() + " (moto " + r.placa() + ")";
        return switch (tipo) {
            case VENCE_HOY -> "Hola " + nombre + ", Rentar Inversiones le recuerda que HOY, " + FECHA.format(hoy)
                    + ", vence la cuota N.° " + r.proximaCuota().numeroCuota() + " de su crédito " + credito + " por "
                    + pesos(r.proximaCuota().valor()) + ". Nuestras fechas de pago son los días 5 y 20 de cada mes. "
                    + "Puede pagar desde el portal o en la oficina de la Carrera 5 # 15-01, Neiva. ¡Gracias por estar al día!";
            case RECORDATORIO -> {
                long dias = ChronoUnit.DAYS.between(hoy, r.proximaCuota().fechaVencimiento());
                yield "Hola " + nombre + ", Rentar Inversiones le recuerda que el " + FECHA.format(r.proximaCuota().fechaVencimiento())
                        + (dias == 1 ? " (mañana)" : " (en " + dias + " días)") + " vence la cuota N.° " + r.proximaCuota().numeroCuota()
                        + " de su crédito " + credito + " por " + pesos(r.proximaCuota().valor())
                        + ". Recuerde que las fechas de pago son los días 5 y 20 de cada mes.";
            }
            // Sin cifras: el aviso solo informa que hay cuotas vencidas (el detalle lo ve el cliente en su portal).
            case MORA -> "Hola " + nombre + ", Rentar Inversiones le informa que su crédito " + credito
                    + " tiene cuotas vencidas y se encuentra en mora. Le invitamos a ponerse al día y pagar en las fechas "
                    + "establecidas (días 5 y 20) para evitar reportes. Línea de atención y WhatsApp: " + lineaAtencion + ".";
            case GENERAL -> "Hola " + nombre + ", ";
        };
    }

    /** Aviso al codeudor cuando el credito que respalda esta en mora. */
    String textoCodeudor(Credito c, CreditoResumen r) {
        return "Hola " + primerNombre(c.getCodeudor().getNombres()) + ", Rentar Inversiones le informa, como codeudor, que el crédito "
                + r.numeroCredito() + " de " + r.cliente() + " (moto " + r.placa() + ") tiene cuotas vencidas y se encuentra "
                + "en mora. Le agradecemos apoyar al titular para ponerse al día y evitar reportes. "
                + "Línea de atención y WhatsApp: " + lineaAtencion + ".";
    }

    // ================================================================== utilidades

    private MensajeCliente guardar(Credito credito, TipoMensaje tipo, String texto, String admin) {
        MensajeCliente m = new MensajeCliente();
        m.setCliente(credito.getCliente());
        m.setCredito(credito);
        m.setTipo(tipo);
        m.setTitulo(titulo(tipo));
        m.setTexto(texto.length() > 700 ? texto.substring(0, 700) : texto);
        m.setFechaEnvio(LocalDateTime.now(reloj));
        m.setEnviadoPor(admin);
        m.setLeido(false);
        return mensajes.save(m);
    }

    private MensajeInfo info(MensajeCliente m) {
        Cliente c = m.getCliente();
        return new MensajeInfo(m.getId(), m.getTipo(), m.getTitulo(), m.getTexto(), m.getFechaEnvio(), m.isLeido(),
                m.getFechaLectura(), m.getCredito() == null ? null : m.getCredito().getNumeroCredito(),
                c.getNombreCompleto(), c.getNumeroDocumento());
    }

    private static List<AvisoSugerido> filtrar(List<AvisoSugerido> lista, TipoMensaje tipo) {
        return lista.stream().filter(a -> a.tipo() == tipo).toList();
    }

    /** Enlace de WhatsApp (wa.me) con el texto ya escrito. Celulares de Colombia: se antepone el 57. */
    static String enlaceWhatsapp(String telefono, String texto) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replaceAll("\\D", "");
        if (digitos.length() == 10) {
            digitos = "57" + digitos;
        }
        if (digitos.length() < 11) {
            return null;
        }
        return "https://wa.me/" + digitos + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String pesos(BigDecimal valor) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(ES_CO);
        s.setGroupingSeparator('.');
        return "$" + new DecimalFormat("#,##0", s).format(valor == null ? BigDecimal.ZERO : valor.setScale(0, RoundingMode.HALF_UP));
    }

    private static String primerNombre(String nombres) {
        if (nombres == null || nombres.isBlank()) {
            return "";
        }
        return nombres.trim().split("\\s+")[0];
    }
}
