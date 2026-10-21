package com.portalcreditos.service;

import com.portalcreditos.dto.admin.AdminDtos.CreditoRequest;
import com.portalcreditos.dto.admin.AdminDtos.SimulacionPlanRequest;
import com.portalcreditos.dto.credito.CreditoDtos.CreditoDetalle;
import com.portalcreditos.entity.Credito;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creacion de creditos desde el panel administrativo. */
@Service
public class CreditoAdminService {

    private final CreditoFactory factory;
    private final CreditoConsultaService consulta;
    private final AmortizacionService amortizacion;
    private final AuditoriaService auditoria;

    public CreditoAdminService(CreditoFactory factory, CreditoConsultaService consulta,
                               AmortizacionService amortizacion, AuditoriaService auditoria) {
        this.factory = factory;
        this.consulta = consulta;
        this.amortizacion = amortizacion;
        this.auditoria = auditoria;
    }

    /** Crea el credito, genera su plan de pagos y devuelve el detalle. */
    @Transactional
    public CreditoDetalle crear(CreditoRequest r, String username, String ip) {
        Credito c = factory.crear(r);
        auditoria.registrar(username, "CREAR_CREDITO", "credito", c.getNumeroCredito(),
                "Cliente " + c.getCliente().getNumeroDocumento() + " - placa " + c.getVehiculo().getPlaca(), ip);
        return consulta.detalle(c.getId());
    }

    /** Vista previa del plan de pagos (no guarda nada). */
    public AmortizacionService.PlanAmortizacion simularPlan(SimulacionPlanRequest r) {
        try {
            return amortizacion.calcular(r.valorFinanciado(), r.tasaInteresMensual(), r.plazoMeses(), r.fechaPrimeraCuota());
        } catch (IllegalArgumentException e) {
            throw com.portalcreditos.exception.NegocioException.solicitudInvalida(e.getMessage());
        }
    }
}
