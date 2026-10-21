package com.portalcreditos.controller;

import com.portalcreditos.dto.pago.PagoDtos.Comprobante;
import com.portalcreditos.dto.pago.PagoDtos.OpcionesPago;
import com.portalcreditos.dto.pago.PagoDtos.PagoHistorial;
import com.portalcreditos.dto.pago.PagoDtos.PagoRequest;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.PagoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Pagos simulados e historial de pagos del cliente autenticado (solo rol CLIENTE). */
@RestController
@RequestMapping("/api/cliente/creditos/{creditoId}/pagos")
public class ClientePagoController {

    private final PagoService servicio;
    private final UsuarioActual usuarioActual;

    public ClientePagoController(PagoService servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** Valores sugeridos (lo vencido, hasta la proxima cuota, maximo) y medios disponibles. */
    @GetMapping("/opciones")
    public OpcionesPago opciones(@PathVariable Long creditoId) {
        return servicio.opcionesDelCliente(usuarioActual.getClienteId(), creditoId);
    }

    /** Realiza un pago simulado y devuelve el comprobante. */
    @PostMapping
    public Comprobante pagar(@PathVariable Long creditoId, @Valid @RequestBody PagoRequest solicitud,
                             HttpServletRequest request) {
        return servicio.pagarComoCliente(usuarioActual.getClienteId(), creditoId, solicitud,
                usuarioActual.getUsername(), request.getRemoteAddr());
    }

    /** Historial de pagos del credito, del mas reciente al mas antiguo. */
    @GetMapping
    public List<PagoHistorial> historial(@PathVariable Long creditoId) {
        return servicio.historialDelCliente(usuarioActual.getClienteId(), creditoId);
    }

    /** Comprobante de un pago especifico. */
    @GetMapping("/{pagoId}")
    public Comprobante comprobante(@PathVariable Long creditoId, @PathVariable Long pagoId) {
        return servicio.comprobanteDelCliente(usuarioActual.getClienteId(), creditoId, pagoId);
    }
}
