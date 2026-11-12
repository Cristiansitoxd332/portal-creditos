package com.portalcreditos.service;

import com.portalcreditos.dto.documento.DocumentoDtos.PazYSalvoEstado;
import com.portalcreditos.dto.documento.DocumentoDtos.VerificacionPazYSalvo;
import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.PazYSalvo;
import com.portalcreditos.entity.Vehiculo;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.PazYSalvoRepository;
import com.portalcreditos.util.Textos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Paz y salvo:
 * - Solo se expide si el credito esta CANCELADO (todas las cuotas pagadas).
 * - La primera vez se crea un registro con un codigo de verificacion unico; despues se reutiliza.
 * - Cualquier persona puede verificar el codigo en una pagina publica; los datos personales
 *   se muestran enmascarados para proteger la privacidad del titular.
 */
@Service
public class PazYSalvoService {

    private static final String AVISO = "Verificación del prototipo académico con datos ficticios. "
            + "El certificado no tiene validez legal.";

    private final CreditoRepository creditos;
    private final PazYSalvoRepository pazYSalvos;
    private final SituacionCreditoService situacionService;
    private final DocumentoPdfService pdfService;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final String urlFrontend;

    public PazYSalvoService(CreditoRepository creditos, PazYSalvoRepository pazYSalvos,
                            SituacionCreditoService situacionService, DocumentoPdfService pdfService,
                            AuditoriaService auditoria, Clock reloj, @Value("${app.frontend-url}") String urlFrontend) {
        this.creditos = creditos;
        this.pazYSalvos = pazYSalvos;
        this.situacionService = situacionService;
        this.pdfService = pdfService;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.urlFrontend = urlFrontend.endsWith("/") ? urlFrontend.substring(0, urlFrontend.length() - 1) : urlFrontend;
    }

    @Transactional(readOnly = true)
    public PazYSalvoEstado estadoDelCliente(Long clienteId, Long creditoId) {
        Credito c = delCliente(clienteId, creditoId);
        if (!estaCancelado(c)) {
            return new PazYSalvoEstado(false,
                    "El paz y salvo estará disponible cuando el crédito esté cancelado en su totalidad.", null, null);
        }
        return pazYSalvos.findByCreditoId(c.getId())
                .map(p -> new PazYSalvoEstado(true, "Paz y salvo disponible para descarga.", p.getCodigoVerificacion(), p.getFechaEmision()))
                .orElse(new PazYSalvoEstado(true, "Su crédito está cancelado. Puede generar el paz y salvo.", null, null));
    }

    @Transactional
    public byte[] generarDelCliente(Long clienteId, Long creditoId, String username, String ip) {
        return generar(delCliente(clienteId, creditoId), username, ip);
    }

    @Transactional
    public byte[] generarComoAdmin(Long creditoId, String username, String ip) {
        Credito c = creditos.findById(creditoId).orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado"));
        return generar(c, username, ip);
    }

    /** Devuelve el paz y salvo existente o lo crea. Lo usa tambien la solicitud de levantamiento. */
    @Transactional
    public PazYSalvo obtenerOCrear(Credito c) {
        if (!estaCancelado(c)) {
            throw NegocioException.conflicto("El paz y salvo solo se expide cuando el crédito está cancelado en su totalidad.");
        }
        return pazYSalvos.findByCreditoId(c.getId()).orElseGet(() -> {
            PazYSalvo p = new PazYSalvo();
            p.setCredito(c);
            p.setCodigoVerificacion(nuevoCodigo());
            p.setFechaEmision(LocalDateTime.now(reloj));
            return pazYSalvos.save(p);
        });
    }

    /** Verificacion publica (sin iniciar sesion). */
    @Transactional(readOnly = true)
    public VerificacionPazYSalvo verificar(String codigo) {
        String limpio = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        return pazYSalvos.findByCodigoVerificacion(limpio)
                .map(p -> {
                    Credito c = p.getCredito();
                    Cliente cl = c.getCliente();
                    return new VerificacionPazYSalvo(true, p.getCodigoVerificacion(), c.getNumeroCredito(),
                            Textos.enmascararNombre(cl.getNombres(), cl.getApellidos()),
                            Textos.enmascararDocumento(cl.getNumeroDocumento()),
                            c.getVehiculo().getPlaca(), c.getFechaCancelacion(), p.getFechaEmision(), AVISO);
                })
                .orElse(new VerificacionPazYSalvo(false, limpio, null, null, null, null, null, null,
                        "El código no corresponde a ningún paz y salvo emitido. " + AVISO));
    }

    private byte[] generar(Credito c, String username, String ip) {
        PazYSalvo p = obtenerOCrear(c);
        Cliente cl = c.getCliente();
        Vehiculo v = c.getVehiculo();
        LocalDate cancelacion = c.getFechaCancelacion();
        auditoria.registrar(username, "DESCARGA_PAZ_Y_SALVO", "paz_y_salvo", p.getCodigoVerificacion(),
                "Credito " + c.getNumeroCredito(), ip);
        return pdfService.pazYSalvo(new DocumentoPdfService.DatosPazYSalvo(
                cl.getNombreCompleto(), cl.getTipoDocumento().name(), cl.getNumeroDocumento(), c.getNumeroCredito(),
                v.getMarca() + " " + v.getLinea() + " modelo " + v.getModelo(), v.getPlaca(), c.getValorFinanciado(),
                c.getFechaDesembolso(), cancelacion, p.getCodigoVerificacion(),
                urlBase() + "/verificar/" + p.getCodigoVerificacion(),
                LocalDateTime.now(reloj))); // el certificado se expide con la fecha y hora en que se descarga
    }

    private boolean estaCancelado(Credito c) {
        return situacionService.evaluar(c, c.getCuotas(), LocalDate.now(reloj)).estado() == EstadoCredito.CANCELADO;
    }

    private Credito delCliente(Long clienteId, Long creditoId) {
        return creditos.findByIdAndClienteId(creditoId, clienteId)
                .orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado"));
    }

    private String nuevoCodigo() {
        String codigo;
        do {
            codigo = "PYS-" + LocalDate.now(reloj).getYear() + "-" + Textos.codigo(8);
        } while (pazYSalvos.findByCodigoVerificacion(codigo).isPresent());
        return codigo;
    }

    /**
     * Direccion del frontend para el QR. Si la peticion llega desde el navegador (cabecera Origin)
     * con una direccion local o de la red (p. ej. un celular en el mismo WiFi), se usa esa; asi el QR
     * abre la verificacion desde cualquier equipo. Si no, se usa app.frontend-url.
     */
    private String urlBase() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            String origen = atributos.getRequest().getHeader("Origin");
            if (origen != null && origen.matches("^https?://(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d{1,5})?$")) {
                return origen;
            }
        }
        return urlFrontend;
    }
}
