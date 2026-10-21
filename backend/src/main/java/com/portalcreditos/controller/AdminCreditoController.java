package com.portalcreditos.controller;

import com.portalcreditos.dto.admin.AdminDtos.CreditoRequest;
import com.portalcreditos.dto.admin.AdminDtos.SimulacionPlanRequest;
import com.portalcreditos.dto.credito.CreditoDtos.CreditoDetalle;
import com.portalcreditos.dto.credito.CreditoDtos.CreditoResumen;
import com.portalcreditos.dto.credito.CreditoDtos.PlanPagos;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.AmortizacionService;
import com.portalcreditos.service.CreditoAdminService;
import com.portalcreditos.service.CreditoConsultaService;
import com.portalcreditos.service.PazYSalvoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consulta y creacion de creditos (solo rol ADMIN). */
@RestController
@RequestMapping("/api/admin/creditos")
public class AdminCreditoController {

    private final CreditoConsultaService consulta;
    private final CreditoAdminService administracion;
    private final PazYSalvoService pazYSalvo;
    private final UsuarioActual usuario;

    public AdminCreditoController(CreditoConsultaService consulta, CreditoAdminService administracion,
                                  PazYSalvoService pazYSalvo, UsuarioActual usuario) {
        this.consulta = consulta;
        this.administracion = administracion;
        this.pazYSalvo = pazYSalvo;
        this.usuario = usuario;
    }

    @GetMapping
    public List<CreditoResumen> listar() {
        return consulta.listarTodos();
    }

    @GetMapping("/{id}")
    public CreditoDetalle detalle(@PathVariable Long id) {
        return consulta.detalle(id);
    }

    @GetMapping("/{id}/cuotas")
    public PlanPagos cuotas(@PathVariable Long id) {
        return consulta.plan(id);
    }

    /** Crea un credito con su vehiculo; el plan de pagos se calcula automaticamente. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditoDetalle crear(@Valid @RequestBody CreditoRequest r, HttpServletRequest request) {
        return administracion.crear(r, usuario.getUsername(), request.getRemoteAddr());
    }

    /** Vista previa del plan de pagos antes de crear el credito (no guarda nada). */
    @PostMapping("/simular-plan")
    public AmortizacionService.PlanAmortizacion simularPlan(@Valid @RequestBody SimulacionPlanRequest r) {
        return administracion.simularPlan(r);
    }

    @GetMapping("/{id}/paz-y-salvo")
    public ResponseEntity<byte[]> pazYSalvo(@PathVariable Long id, HttpServletRequest request) {
        return Descargas.pdf(pazYSalvo.generarComoAdmin(id, usuario.getUsername(), request.getRemoteAddr()),
                "paz-y-salvo-credito-" + id + ".pdf");
    }
}
