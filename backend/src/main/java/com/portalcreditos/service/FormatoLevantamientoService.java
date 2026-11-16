package com.portalcreditos.service;

import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.SolicitudLevantamiento;
import com.portalcreditos.entity.Vehiculo;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.CreditoRepository;
import com.portalcreditos.repository.SolicitudLevantamientoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Formato oficial de Rentar "Solicitud de levantamiento de prenda" en PDF, prellenado con los datos del
 * titular y de la moto. El cliente lo imprime, lo firma, hace el reconocimiento de firma en notaria y lo envia
 * con los anexos al correo de garantias mobiliarias.
 */
@Service
public class FormatoLevantamientoService {

    private final CreditoRepository creditos;
    private final SolicitudLevantamientoRepository solicitudes;
    private final DocumentoPdfService pdf;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final String cuentaRecaudo;

    public FormatoLevantamientoService(CreditoRepository creditos, SolicitudLevantamientoRepository solicitudes,
                                       DocumentoPdfService pdf, AuditoriaService auditoria, Clock reloj,
                                       @Value("${app.levantamiento.cuenta-recaudo:7854}") String cuentaRecaudo) {
        this.creditos = creditos;
        this.solicitudes = solicitudes;
        this.pdf = pdf;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.cuentaRecaudo = cuentaRecaudo;
    }

    /** Formato para el cliente dueno del credito (solo si el credito esta cancelado). */
    @Transactional(readOnly = true)
    public byte[] delCliente(Long clienteId, Long creditoId, String usuario, String ip) {
        Credito c = creditos.findByIdAndClienteId(creditoId, clienteId)
                .orElseThrow(() -> NegocioException.noEncontrado("El credito no existe"));
        byte[] archivo = generar(c);
        auditoria.registrar(usuario, "DESCARGAR_FORMATO_LEVANTAMIENTO", "credito", String.valueOf(creditoId),
                "Formato de solicitud de levantamiento " + c.getNumeroCredito(), ip);
        return archivo;
    }

    /** Formato para el administrador (cualquier credito cancelado). */
    @Transactional(readOnly = true)
    public byte[] delAdmin(Long creditoId) {
        Credito c = creditos.findById(creditoId)
                .orElseThrow(() -> NegocioException.noEncontrado("El credito no existe"));
        return generar(c);
    }

    private byte[] generar(Credito c) {
        if (c.getEstado() != EstadoCredito.CANCELADO) {
            throw NegocioException.conflicto("El formato de levantamiento se habilita cuando el credito esta cancelado (paz y salvo).");
        }
        Cliente cl = c.getCliente();
        Vehiculo v = c.getVehiculo();
        String pagoTramite = solicitudes.findByCreditoIdOrderByFechaSolicitudDesc(c.getId()).stream()
                .filter(s -> s.getReferenciaPagoTramite() != null)
                .findFirst()
                .map(FormatoLevantamientoService::textoPago)
                .orElse(null);
        String modelo = ((v.getLinea() == null ? "" : v.getLinea()) + (v.getModelo() == null ? "" : " " + v.getModelo())).trim();
        return pdf.solicitudLevantamiento(new DocumentoPdfService.DatosSolicitudLevantamiento(
                LocalDate.now(reloj), cl.getCiudad(), cl.getNombreCompleto(), cl.getTipoDocumento().name(),
                cl.getNumeroDocumento(), v.getMarca(), modelo, v.getColor(), v.getNumeroChasis(), v.getNumeroMotor(),
                v.getPlaca(), cl.getTelefono(), cl.getDireccion(), cl.getCorreo(), c.getNumeroCredito(), pagoTramite,
                cuentaRecaudo));
    }

    private static String textoPago(SolicitudLevantamiento s) {
        return "Pago del trámite registrado en el portal: referencia " + s.getReferenciaPagoTramite()
                + " (" + s.getMedioPagoTramite() + ", cuenta de recaudo N.° " + s.getCuentaRecaudo() + ").";
    }
}
