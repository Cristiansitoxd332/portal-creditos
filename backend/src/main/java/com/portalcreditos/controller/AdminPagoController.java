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

/**
 * Pagos desde el panel administrativo (solo rol ADMIN).
 * Permite registrar pagos recibidos por otros medios (efectivo, transferencia, consignacion).
 */
@RestController
@RequestMapping("/api/admin/creditos/{creditoId}/pagos")
public class AdminPagoController {

    private final PagoService servicio;
    private final UsuarioActual usuarioActual;

    public AdminPagoController(PagoService servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping("/opciones")
    public OpcionesPago opciones(@PathVariable Long creditoId) {
        return servicio.opciones(creditoId);
    }

    @PostMapping
    public Comprobante registrar(@PathVariable Long creditoId, @Valid @RequestBody PagoRequest solicitud,
                                 HttpServletRequest request) {
        return servicio.registrarComoAdmin(creditoId, solicitud, usuarioActual.getUsername(), request.getRemoteAddr());
    }

    @GetMapping
    public List<PagoHistorial> historial(@PathVariable Long creditoId) {
        return servicio.historial(creditoId);
    }

    @GetMapping("/{pagoId}")
    public Comprobante comprobante(@PathVariable Long creditoId, @PathVariable Long pagoId) {
        return servicio.comprobante(creditoId, pagoId);
    }
}
