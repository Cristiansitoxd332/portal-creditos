package com.portalcreditos.controller;

import com.portalcreditos.dto.credito.CreditoDtos.CreditoDetalle;
import com.portalcreditos.dto.credito.CreditoDtos.CreditoResumen;
import com.portalcreditos.dto.credito.CreditoDtos.PlanPagos;
import com.portalcreditos.service.CreditoConsultaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consulta de todos los creditos (solo rol ADMIN). */
@RestController
@RequestMapping("/api/admin/creditos")
public class AdminCreditoController {

    private final CreditoConsultaService servicio;

    public AdminCreditoController(CreditoConsultaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<CreditoResumen> listar() {
        return servicio.listarTodos();
    }

    @GetMapping("/{id}")
    public CreditoDetalle detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    @GetMapping("/{id}/cuotas")
    public PlanPagos cuotas(@PathVariable Long id) {
        return servicio.plan(id);
    }
}
