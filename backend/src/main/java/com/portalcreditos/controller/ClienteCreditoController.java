package com.portalcreditos.controller;

import com.portalcreditos.dto.credito.CreditoDtos.CreditoDetalle;
import com.portalcreditos.dto.credito.CreditoDtos.CreditoResumen;
import com.portalcreditos.dto.credito.CreditoDtos.PlanPagos;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.CreditoConsultaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Creditos del cliente autenticado (solo rol CLIENTE).
 * El id del cliente NUNCA viene en la URL: se toma del token, asi nadie puede consultar datos ajenos.
 */
@RestController
@RequestMapping("/api/cliente/creditos")
public class ClienteCreditoController {

    private final CreditoConsultaService servicio;
    private final UsuarioActual usuarioActual;

    public ClienteCreditoController(CreditoConsultaService servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** Lista de creditos del cliente con su semaforo, progreso y proxima cuota. */
    @GetMapping
    public List<CreditoResumen> misCreditos() {
        return servicio.listarDelCliente(usuarioActual.getClienteId());
    }

    /** Detalle de un credito propio. */
    @GetMapping("/{id}")
    public CreditoDetalle detalle(@PathVariable Long id) {
        return servicio.detalleDelCliente(usuarioActual.getClienteId(), id);
    }

    /** Plan de pagos de un credito propio, con el estado de cada cuota. */
    @GetMapping("/{id}/cuotas")
    public PlanPagos cuotas(@PathVariable Long id) {
        return servicio.planDelCliente(usuarioActual.getClienteId(), id);
    }
}
