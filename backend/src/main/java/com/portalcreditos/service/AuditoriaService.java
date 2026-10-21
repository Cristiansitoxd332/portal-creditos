package com.portalcreditos.service;

import com.portalcreditos.entity.Auditoria;
import com.portalcreditos.repository.AuditoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra acciones relevantes en la bitacora.
 * Usa una transaccion independiente (REQUIRES_NEW) para que el registro se guarde
 * incluso si la operacion principal falla (por ejemplo, un inicio de sesion fallido).
 */
@Service
public class AuditoriaService {

    private final AuditoriaRepository repositorio;

    public AuditoriaService(AuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String username, String accion, String entidad, String entidadId,
                          String detalle, String ip) {
        Auditoria a = new Auditoria();
        a.setUsername(recortar(username, 50));
        a.setAccion(accion);
        a.setEntidad(entidad);
        a.setEntidadId(entidadId);
        a.setDetalle(recortar(detalle, 1000));
        a.setDireccionIp(recortar(ip, 45));
        repositorio.save(a);
    }

    private static String recortar(String texto, int max) {
        return texto == null || texto.length() <= max ? texto : texto.substring(0, max);
    }
}
