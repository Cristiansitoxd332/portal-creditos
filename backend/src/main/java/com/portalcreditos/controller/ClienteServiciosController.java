package com.portalcreditos.controller;

import com.portalcreditos.dto.documento.DocumentoDtos.PazYSalvoEstado;
import com.portalcreditos.dto.levantamiento.LevantamientoDtos.EstadoLevantamiento;
import com.portalcreditos.dto.levantamiento.LevantamientoDtos.SolicitudInfo;
import com.portalcreditos.dto.levantamiento.LevantamientoDtos.SolicitudRequest;
import com.portalcreditos.dto.simulador.SimuladorDtos.SimulacionAbono;
import com.portalcreditos.dto.simulador.SimuladorDtos.SimulacionRequest;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.DocumentoPdfService;
import com.portalcreditos.service.LevantamientoService;
import com.portalcreditos.service.PagoService;
import com.portalcreditos.service.PazYSalvoService;
import com.portalcreditos.service.SimuladorAbonoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servicios del cliente sobre UN credito propio: paz y salvo, comprobantes en PDF,
 * simulador de abono a capital y levantamiento de prenda. Solo rol CLIENTE.
 */
@RestController
@RequestMapping("/api/cliente/creditos/{creditoId}")
public class ClienteServiciosController {

    private final PazYSalvoService pazYSalvo;
    private final PagoService pagos;
    private final DocumentoPdfService pdf;
    private final SimuladorAbonoService simulador;
    private final LevantamientoService levantamiento;
    private final UsuarioActual usuario;

    public ClienteServiciosController(PazYSalvoService pazYSalvo, PagoService pagos, DocumentoPdfService pdf,
                                      SimuladorAbonoService simulador, LevantamientoService levantamiento,
                                      UsuarioActual usuario) {
        this.pazYSalvo = pazYSalvo;
        this.pagos = pagos;
        this.pdf = pdf;
        this.simulador = simulador;
        this.levantamiento = levantamiento;
        this.usuario = usuario;
    }

    /** Indica si el paz y salvo esta disponible. */
    @GetMapping("/paz-y-salvo/estado")
    public PazYSalvoEstado estadoPazYSalvo(@PathVariable Long creditoId) {
        return pazYSalvo.estadoDelCliente(usuario.getClienteId(), creditoId);
    }

    /** Descarga el paz y salvo en PDF (409 si el credito no esta cancelado). */
    @GetMapping("/paz-y-salvo")
    public ResponseEntity<byte[]> descargarPazYSalvo(@PathVariable Long creditoId, HttpServletRequest request) {
        byte[] archivo = pazYSalvo.generarDelCliente(usuario.getClienteId(), creditoId, usuario.getUsername(),
                request.getRemoteAddr());
        return Descargas.pdf(archivo, "paz-y-salvo-credito-" + creditoId + ".pdf");
    }

    /** Comprobante de un pago en PDF. */
    @GetMapping("/pagos/{pagoId}/pdf")
    public ResponseEntity<byte[]> comprobantePdf(@PathVariable Long creditoId, @PathVariable Long pagoId) {
        var comprobante = pagos.comprobanteDelCliente(usuario.getClienteId(), creditoId, pagoId);
        return Descargas.pdf(pdf.comprobantePago(comprobante), "comprobante-" + comprobante.referencia() + ".pdf");
    }

    /** Simula un abono extraordinario a capital (no modifica el credito). */
    @PostMapping("/simulador-abono")
    public SimulacionAbono simularAbono(@PathVariable Long creditoId, @Valid @RequestBody SimulacionRequest r) {
        return simulador.simularDelCliente(usuario.getClienteId(), creditoId, r.valorAbono());
    }

    /** Estado del levantamiento de prenda y seguimiento de las solicitudes. */
    @GetMapping("/levantamiento")
    public EstadoLevantamiento estadoLevantamiento(@PathVariable Long creditoId) {
        return levantamiento.estadoDelCliente(usuario.getClienteId(), creditoId);
    }

    /** Radica una solicitud (simulada) de levantamiento de prenda. */
    @PostMapping("/levantamiento")
    public SolicitudInfo solicitarLevantamiento(@PathVariable Long creditoId, @Valid @RequestBody SolicitudRequest r,
                                                HttpServletRequest request) {
        return levantamiento.solicitar(usuario.getClienteId(), creditoId, r.observacion(), r.medioPago(), usuario.getUsername(),
                request.getRemoteAddr());
    }
}
