package com.portalcreditos.controller;

import com.portalcreditos.dto.admin.AdminDtos.Tablero;
import com.portalcreditos.dto.configuracion.ConfiguracionDtos.CanalInfo;
import com.portalcreditos.dto.configuracion.ConfiguracionDtos.CanalRequest;
import com.portalcreditos.dto.configuracion.ConfiguracionDtos.RequisitoInfo;
import com.portalcreditos.dto.configuracion.ConfiguracionDtos.RequisitoRequest;
import com.portalcreditos.dto.levantamiento.LevantamientoDtos.RespuestaRequest;
import com.portalcreditos.dto.levantamiento.LevantamientoDtos.SolicitudInfo;
import com.portalcreditos.entity.Auditoria;
import com.portalcreditos.entity.enums.EstadoSolicitud;
import com.portalcreditos.repository.AuditoriaRepository;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.ConfiguracionService;
import com.portalcreditos.service.DashboardService;
import com.portalcreditos.service.LevantamientoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Panel administrativo (solo rol ADMIN): tablero de indicadores, solicitudes de levantamiento,
 * canales de contacto, requisitos y bitacora de auditoria.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminGestionController {

    private final DashboardService tablero;
    private final LevantamientoService levantamiento;
    private final ConfiguracionService configuracion;
    private final AuditoriaRepository auditoria;
    private final UsuarioActual usuario;

    public AdminGestionController(DashboardService tablero, LevantamientoService levantamiento,
                                  ConfiguracionService configuracion, AuditoriaRepository auditoria,
                                  UsuarioActual usuario) {
        this.tablero = tablero;
        this.levantamiento = levantamiento;
        this.configuracion = configuracion;
        this.auditoria = auditoria;
        this.usuario = usuario;
    }

    // ------------------------------------------------------------------ tablero

    @GetMapping("/tablero")
    public Tablero tablero() {
        return tablero.resumen();
    }

    // ------------------------------------------------------------------ solicitudes de levantamiento

    /** Lista de solicitudes; filtro opcional ?estado=RADICADA|EN_REVISION|APROBADA|RECHAZADA */
    @GetMapping("/solicitudes")
    public List<SolicitudInfo> solicitudes(@RequestParam(required = false) EstadoSolicitud estado) {
        return levantamiento.listar(estado);
    }

    @PatchMapping("/solicitudes/{id}")
    public SolicitudInfo responder(@PathVariable Long id, @Valid @RequestBody RespuestaRequest r, HttpServletRequest request) {
        return levantamiento.responder(id, r, usuario.getUsername(), request.getRemoteAddr());
    }

    // ------------------------------------------------------------------ canales de contacto

    @GetMapping("/canales")
    public List<CanalInfo> canales() {
        return configuracion.todosLosCanales();
    }

    @PostMapping("/canales")
    @ResponseStatus(HttpStatus.CREATED)
    public CanalInfo crearCanal(@Valid @RequestBody CanalRequest r) {
        return configuracion.guardarCanal(null, r, usuario.getUsername());
    }

    @PutMapping("/canales/{id}")
    public CanalInfo editarCanal(@PathVariable Long id, @Valid @RequestBody CanalRequest r) {
        return configuracion.guardarCanal(id, r, usuario.getUsername());
    }

    @DeleteMapping("/canales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCanal(@PathVariable Long id) {
        configuracion.eliminarCanal(id, usuario.getUsername());
    }

    // ------------------------------------------------------------------ requisitos de levantamiento

    @GetMapping("/requisitos")
    public List<RequisitoInfo> requisitos() {
        return configuracion.todosLosRequisitos();
    }

    @PostMapping("/requisitos")
    @ResponseStatus(HttpStatus.CREATED)
    public RequisitoInfo crearRequisito(@Valid @RequestBody RequisitoRequest r) {
        return configuracion.guardarRequisito(null, r, usuario.getUsername());
    }

    @PutMapping("/requisitos/{id}")
    public RequisitoInfo editarRequisito(@PathVariable Long id, @Valid @RequestBody RequisitoRequest r) {
        return configuracion.guardarRequisito(id, r, usuario.getUsername());
    }

    @DeleteMapping("/requisitos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarRequisito(@PathVariable Long id) {
        configuracion.eliminarRequisito(id, usuario.getUsername());
    }

    // ------------------------------------------------------------------ auditoria

    public record RegistroAuditoria(Long id, String usuario, String accion, String entidad, String entidadId,
                                    String detalle, String direccionIp, LocalDateTime fecha) {
    }

    /** Ultimos registros de la bitacora (?cantidad=50 por defecto, maximo 500). */
    @GetMapping("/auditoria")
    public List<RegistroAuditoria> auditoria(@RequestParam(defaultValue = "50") int cantidad) {
        int n = Math.max(1, Math.min(cantidad, 500));
        return auditoria.findAllByOrderByFechaDesc(PageRequest.of(0, n)).getContent().stream()
                .map((Auditoria a) -> new RegistroAuditoria(a.getId(), a.getUsername(), a.getAccion(), a.getEntidad(),
                        a.getEntidadId(), a.getDetalle(), a.getDireccionIp(), a.getFecha()))
                .toList();
    }
}
