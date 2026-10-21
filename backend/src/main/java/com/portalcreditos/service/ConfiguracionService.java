package com.portalcreditos.service;

import com.portalcreditos.dto.configuracion.ConfiguracionDtos.CanalInfo;
import com.portalcreditos.dto.configuracion.ConfiguracionDtos.CanalRequest;
import com.portalcreditos.dto.configuracion.ConfiguracionDtos.RequisitoInfo;
import com.portalcreditos.dto.configuracion.ConfiguracionDtos.RequisitoRequest;
import com.portalcreditos.entity.CanalContacto;
import com.portalcreditos.entity.RequisitoLevantamiento;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.CanalContactoRepository;
import com.portalcreditos.repository.RequisitoLevantamientoRepository;
import com.portalcreditos.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Contenido configurable desde el panel administrativo:
 * canales de atencion (WhatsApp, telefono, correo...) y requisitos de levantamiento de prenda.
 * Asi la empresa puede actualizar esta informacion sin modificar el codigo.
 */
@Service
public class ConfiguracionService {

    private final CanalContactoRepository canales;
    private final RequisitoLevantamientoRepository requisitos;
    private final AuditoriaService auditoria;

    public ConfiguracionService(CanalContactoRepository canales, RequisitoLevantamientoRepository requisitos,
                                AuditoriaService auditoria) {
        this.canales = canales;
        this.requisitos = requisitos;
        this.auditoria = auditoria;
    }

    // ----------------------------------------------------------------- canales

    @Transactional(readOnly = true)
    public List<CanalInfo> canalesActivos() {
        return canales.findByActivoTrueOrderByOrdenAsc().stream().map(this::info).toList();
    }

    @Transactional(readOnly = true)
    public List<CanalInfo> todosLosCanales() {
        return canales.findAllByOrderByOrdenAsc().stream().map(this::info).toList();
    }

    @Transactional
    public CanalInfo guardarCanal(Long id, CanalRequest r, String username) {
        if (!Textos.enlaceSeguro(r.enlace())) {
            throw NegocioException.solicitudInvalida("El enlace debe comenzar por https://, http://, tel: o mailto:");
        }
        CanalContacto c = id == null ? new CanalContacto()
                : canales.findById(id).orElseThrow(() -> NegocioException.noEncontrado("Canal no encontrado"));
        c.setTipo(r.tipo());
        c.setNombre(r.nombre().trim());
        c.setValor(r.valor().trim());
        c.setEnlace(Textos.limpiar(r.enlace()));
        c.setHorario(Textos.limpiar(r.horario()));
        c.setOrden(r.orden() == null ? 0 : r.orden());
        c.setActivo(r.activo() == null || r.activo());
        canales.save(c);
        auditoria.registrar(username, id == null ? "CREAR_CANAL" : "EDITAR_CANAL", "canal_contacto",
                String.valueOf(c.getId()), c.getNombre(), null);
        return info(c);
    }

    @Transactional
    public void eliminarCanal(Long id, String username) {
        CanalContacto c = canales.findById(id).orElseThrow(() -> NegocioException.noEncontrado("Canal no encontrado"));
        canales.delete(c);
        auditoria.registrar(username, "ELIMINAR_CANAL", "canal_contacto", String.valueOf(id), c.getNombre(), null);
    }

    // ----------------------------------------------------------------- requisitos

    @Transactional(readOnly = true)
    public List<RequisitoInfo> requisitosActivos() {
        return requisitos.findByActivoTrueOrderByOrdenAsc().stream().map(this::info).toList();
    }

    @Transactional(readOnly = true)
    public List<RequisitoInfo> todosLosRequisitos() {
        return requisitos.findAllByOrderByOrdenAsc().stream().map(this::info).toList();
    }

    @Transactional
    public RequisitoInfo guardarRequisito(Long id, RequisitoRequest r, String username) {
        RequisitoLevantamiento q = id == null ? new RequisitoLevantamiento()
                : requisitos.findById(id).orElseThrow(() -> NegocioException.noEncontrado("Requisito no encontrado"));
        q.setTitulo(r.titulo().trim());
        q.setDescripcion(r.descripcion().trim());
        q.setOrden(r.orden() == null ? 0 : r.orden());
        q.setActivo(r.activo() == null || r.activo());
        requisitos.save(q);
        auditoria.registrar(username, id == null ? "CREAR_REQUISITO" : "EDITAR_REQUISITO", "requisito_levantamiento",
                String.valueOf(q.getId()), q.getTitulo(), null);
        return info(q);
    }

    @Transactional
    public void eliminarRequisito(Long id, String username) {
        RequisitoLevantamiento q = requisitos.findById(id)
                .orElseThrow(() -> NegocioException.noEncontrado("Requisito no encontrado"));
        requisitos.delete(q);
        auditoria.registrar(username, "ELIMINAR_REQUISITO", "requisito_levantamiento", String.valueOf(id), q.getTitulo(), null);
    }

    private CanalInfo info(CanalContacto c) {
        return new CanalInfo(c.getId(), c.getTipo(), c.getNombre(), c.getValor(), c.getEnlace(), c.getHorario(),
                c.getOrden(), c.isActivo());
    }

    private RequisitoInfo info(RequisitoLevantamiento q) {
        return new RequisitoInfo(q.getId(), q.getTitulo(), q.getDescripcion(), q.getOrden(), q.isActivo());
    }
}
