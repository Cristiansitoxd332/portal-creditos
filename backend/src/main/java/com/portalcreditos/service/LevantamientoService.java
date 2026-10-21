package com.portalcreditos.service;

import com.portalcreditos.dto.levantamiento.LevantamientoDtos.EstadoLevantamiento;
import com.portalcreditos.dto.levantamiento.LevantamientoDtos.PasoSeguimiento;
import com.portalcreditos.dto.levantamiento.LevantamientoDtos.RespuestaRequest;
import com.portalcreditos.dto.levantamiento.LevantamientoDtos.SolicitudInfo;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.SolicitudLevantamiento;
import com.portalcreditos.entity.Usuario;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoPrenda;
import com.portalcreditos.entity.enums.EstadoSolicitud;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.SolicitudLevantamientoRepository;
import com.portalcreditos.repository.UsuarioRepository;
import com.portalcreditos.util.Textos;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Solicitudes SIMULADAS de levantamiento de prenda.
 *
 * Reglas:
 * - Solo se puede solicitar si el credito esta cancelado y la prenda sigue vigente.
 * - No puede haber dos solicitudes activas (RADICADA o EN_REVISION) para el mismo credito.
 * - Flujo: RADICADA -> EN_REVISION -> APROBADA o RECHAZADA (los estados finales no cambian).
 * - Al radicar, la prenda pasa a EN_TRAMITE; si se aprueba, LEVANTADA; si se rechaza, vuelve a VIGENTE.
 *
 * El tramite real lo realiza la entidad por sus canales oficiales; aqui solo se simula el seguimiento.
 */
@Service
public class LevantamientoService {

    private static final Set<EstadoSolicitud> ACTIVAS = Set.of(EstadoSolicitud.RADICADA, EstadoSolicitud.EN_REVISION);
    private static final String AVISO = "Solicitud simulada en el prototipo. El trámite real se realiza con la entidad "
            + "por sus canales oficiales y ante el organismo de tránsito.";

    private final CreditoRepository creditos;
    private final SolicitudLevantamientoRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final SituacionCreditoService situacionService;
    private final PazYSalvoService pazYSalvoService;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public LevantamientoService(CreditoRepository creditos, SolicitudLevantamientoRepository solicitudes,
                                UsuarioRepository usuarios, SituacionCreditoService situacionService,
                                PazYSalvoService pazYSalvoService, AuditoriaService auditoria, Clock reloj) {
        this.creditos = creditos;
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.situacionService = situacionService;
        this.pazYSalvoService = pazYSalvoService;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    // ================================================================= cliente

    @Transactional(readOnly = true)
    public EstadoLevantamiento estadoDelCliente(Long clienteId, Long creditoId) {
        Credito c = delCliente(clienteId, creditoId);
        String motivo = motivoNoPuedeSolicitar(c);
        List<SolicitudInfo> lista = solicitudes.findByCreditoIdOrderByFechaSolicitudDesc(c.getId()).stream()
                .map(this::info).toList();
        return new EstadoLevantamiento(c.getId(), c.getNumeroCredito(), c.getEstadoPrenda(), motivo == null,
                motivo == null ? "Puede radicar la solicitud de levantamiento de prenda." : motivo, lista, AVISO);
    }

    @Transactional
    public SolicitudInfo solicitar(Long clienteId, Long creditoId, String observacion, String username, String ip) {
        Credito c = delCliente(clienteId, creditoId);
        String motivo = motivoNoPuedeSolicitar(c);
        if (motivo != null) {
            throw NegocioException.conflicto(motivo);
        }
        pazYSalvoService.obtenerOCrear(c); // el paz y salvo es requisito; se expide si aun no existe

        SolicitudLevantamiento s = new SolicitudLevantamiento();
        s.setCredito(c);
        s.setNumeroRadicado(nuevoRadicado());
        s.setFechaSolicitud(LocalDateTime.now(reloj));
        s.setEstado(EstadoSolicitud.RADICADA);
        s.setObservacionCliente(Textos.limpiar(observacion));
        solicitudes.save(s);
        c.setEstadoPrenda(EstadoPrenda.EN_TRAMITE);

        auditoria.registrar(username, "CREAR_SOLICITUD", "solicitud_levantamiento", s.getNumeroRadicado(),
                "Credito " + c.getNumeroCredito(), ip);
        return info(s);
    }

    // ================================================================= administrador

    @Transactional(readOnly = true)
    public List<SolicitudInfo> listar(EstadoSolicitud estado) {
        List<SolicitudLevantamiento> lista = estado == null
                ? solicitudes.findAll(Sort.by(Sort.Direction.DESC, "fechaSolicitud"))
                : solicitudes.findByEstadoOrderByFechaSolicitudAsc(estado);
        return lista.stream().map(this::info).toList();
    }

    @Transactional
    public SolicitudInfo responder(Long solicitudId, RespuestaRequest r, String username, String ip) {
        SolicitudLevantamiento s = solicitudes.findById(solicitudId)
                .orElseThrow(() -> NegocioException.noEncontrado("Solicitud no encontrada"));
        EstadoSolicitud actual = s.getEstado();
        EstadoSolicitud nuevo = r.estado();

        boolean valido = switch (actual) {
            case RADICADA -> nuevo == EstadoSolicitud.EN_REVISION || nuevo == EstadoSolicitud.APROBADA
                    || nuevo == EstadoSolicitud.RECHAZADA;
            case EN_REVISION -> nuevo == EstadoSolicitud.APROBADA || nuevo == EstadoSolicitud.RECHAZADA;
            case APROBADA, RECHAZADA -> false;
        };
        if (!valido) {
            throw NegocioException.conflicto("No se puede pasar una solicitud de " + actual + " a " + nuevo + ".");
        }
        if (nuevo == EstadoSolicitud.RECHAZADA && Textos.limpiar(r.respuesta()) == null) {
            throw NegocioException.solicitudInvalida("Debe indicar el motivo del rechazo.");
        }

        Usuario admin = usuarios.findByUsername(username).orElse(null);
        s.setEstado(nuevo);
        s.setAtendidaPor(admin);
        if (Textos.limpiar(r.respuesta()) != null) {
            s.setRespuestaAdmin(r.respuesta().trim());
        }
        if (nuevo == EstadoSolicitud.APROBADA || nuevo == EstadoSolicitud.RECHAZADA) {
            s.setFechaRespuesta(LocalDateTime.now(reloj));
            s.getCredito().setEstadoPrenda(nuevo == EstadoSolicitud.APROBADA ? EstadoPrenda.LEVANTADA : EstadoPrenda.VIGENTE);
        }
        auditoria.registrar(username, "RESPONDER_SOLICITUD", "solicitud_levantamiento", s.getNumeroRadicado(),
                actual + " -> " + nuevo, ip);
        return info(s);
    }

    // ================================================================= utilidades

    /** Devuelve el motivo por el que NO se puede solicitar, o null si si se puede. */
    private String motivoNoPuedeSolicitar(Credito c) {
        EstadoCredito estado = situacionService.evaluar(c, c.getCuotas(), LocalDate.now(reloj)).estado();
        if (estado != EstadoCredito.CANCELADO) {
            return "Para solicitar el levantamiento de prenda, el crédito debe estar cancelado en su totalidad.";
        }
        if (c.getEstadoPrenda() == EstadoPrenda.LEVANTADA) {
            return "La prenda de este vehículo ya fue levantada.";
        }
        if (solicitudes.existsByCreditoIdAndEstadoIn(c.getId(), ACTIVAS)) {
            return "Ya tiene una solicitud en trámite para este crédito.";
        }
        return null;
    }

    private SolicitudInfo info(SolicitudLevantamiento s) {
        Credito c = s.getCredito();
        EstadoSolicitud e = s.getEstado();
        boolean finalizada = e == EstadoSolicitud.APROBADA || e == EstadoSolicitud.RECHAZADA;
        String nombreFinal = e == EstadoSolicitud.RECHAZADA ? "Rechazada" : "Aprobada - prenda levantada";
        List<PasoSeguimiento> pasos = List.of(
                new PasoSeguimiento("Solicitud radicada", true, s.getFechaSolicitud()),
                new PasoSeguimiento("En revisión de documentos", e != EstadoSolicitud.RADICADA, null),
                new PasoSeguimiento(nombreFinal, finalizada, s.getFechaRespuesta()));
        return new SolicitudInfo(s.getId(), s.getNumeroRadicado(), c.getId(), c.getNumeroCredito(),
                c.getCliente().getNombreCompleto(), c.getCliente().getNumeroDocumento(), c.getVehiculo().getPlaca(),
                s.getFechaSolicitud(), e, s.getObservacionCliente(), s.getRespuestaAdmin(), s.getFechaRespuesta(), pasos);
    }

    private Credito delCliente(Long clienteId, Long creditoId) {
        return creditos.findByIdAndClienteId(creditoId, clienteId)
                .orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado"));
    }

    private String nuevoRadicado() {
        int anio = LocalDate.now(reloj).getYear();
        long consecutivo = solicitudes.count() + 1;
        String radicado;
        do {
            radicado = String.format("RAD-%d-%06d", anio, consecutivo++);
        } while (solicitudes.existsByNumeroRadicado(radicado));
        return radicado;
    }
}
