package com.portalcreditos.controller;

import com.portalcreditos.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint TEMPORAL de diagnostico: confirma que cada entidad JPA lee
 * correctamente su tabla. Se eliminara cuando se active la seguridad.
 */
@RestController
@RequestMapping("/api/diagnostico")
public class DiagnosticoController {

    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final VehiculoRepository vehiculos;
    private final CreditoRepository creditos;
    private final CuotaRepository cuotas;
    private final PagoRepository pagos;
    private final AplicacionPagoRepository aplicaciones;
    private final PazYSalvoRepository pazYSalvos;
    private final RequisitoLevantamientoRepository requisitos;
    private final SolicitudLevantamientoRepository solicitudes;
    private final CanalContactoRepository canales;
    private final ImportacionRepository importaciones;
    private final ImportacionErrorRepository erroresImportacion;
    private final AuditoriaRepository auditoria;

    public DiagnosticoController(ClienteRepository clientes, UsuarioRepository usuarios,
                                 VehiculoRepository vehiculos, CreditoRepository creditos,
                                 CuotaRepository cuotas, PagoRepository pagos,
                                 AplicacionPagoRepository aplicaciones, PazYSalvoRepository pazYSalvos,
                                 RequisitoLevantamientoRepository requisitos,
                                 SolicitudLevantamientoRepository solicitudes,
                                 CanalContactoRepository canales, ImportacionRepository importaciones,
                                 ImportacionErrorRepository erroresImportacion,
                                 AuditoriaRepository auditoria) {
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.vehiculos = vehiculos;
        this.creditos = creditos;
        this.cuotas = cuotas;
        this.pagos = pagos;
        this.aplicaciones = aplicaciones;
        this.pazYSalvos = pazYSalvos;
        this.requisitos = requisitos;
        this.solicitudes = solicitudes;
        this.canales = canales;
        this.importaciones = importaciones;
        this.erroresImportacion = erroresImportacion;
        this.auditoria = auditoria;
    }

    /** Carga TODOS los registros de cada tabla a traves de su entidad y devuelve la cantidad. */
    @GetMapping("/tablas")
    @Transactional(readOnly = true)
    public Map<String, Integer> contarRegistros() {
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("cliente", clientes.findAll().size());
        r.put("usuario", usuarios.findAll().size());
        r.put("vehiculo", vehiculos.findAll().size());
        r.put("credito", creditos.findAll().size());
        r.put("cuota", cuotas.findAll().size());
        r.put("pago", pagos.findAll().size());
        r.put("aplicacion_pago", aplicaciones.findAll().size());
        r.put("paz_y_salvo", pazYSalvos.findAll().size());
        r.put("requisito_levantamiento", requisitos.findAll().size());
        r.put("solicitud_levantamiento", solicitudes.findAll().size());
        r.put("canal_contacto", canales.findAll().size());
        r.put("importacion", importaciones.findAll().size());
        r.put("importacion_error", erroresImportacion.findAll().size());
        r.put("auditoria", auditoria.findAll().size());
        return r;
    }

    /** Resumen de los creditos usando una consulta con proyeccion. */
    @GetMapping("/creditos")
    public List<CreditoRepository.ResumenCreditoView> resumenCreditos() {
        return creditos.listarResumen();
    }
}
