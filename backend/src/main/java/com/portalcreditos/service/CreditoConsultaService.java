package com.portalcreditos.service;

import com.portalcreditos.dto.credito.CreditoDtos.CreditoDetalle;
import com.portalcreditos.dto.credito.CreditoDtos.CreditoResumen;
import com.portalcreditos.dto.credito.CreditoDtos.CuotaInfo;
import com.portalcreditos.dto.credito.CreditoDtos.PlanPagos;
import com.portalcreditos.dto.credito.CreditoDtos.ProximaCuota;
import com.portalcreditos.dto.credito.CreditoDtos.VehiculoInfo;
import com.portalcreditos.entity.Cliente;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.Vehiculo;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.CreditoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Consultas de creditos para el portal del cliente y el panel administrativo.
 *
 * Seguridad: los metodos "DelCliente" buscan el credito por id Y por clienteId (tomado del token).
 * Si un cliente intenta ver un credito ajeno, recibe 404, como si no existiera:
 * asi no se revela informacion de otros clientes.
 */
@Service
@Transactional(readOnly = true)
public class CreditoConsultaService {

    private final CreditoRepository creditos;
    private final SituacionCreditoService situacionService;
    private final Clock reloj;

    public CreditoConsultaService(CreditoRepository creditos, SituacionCreditoService situacionService, Clock reloj) {
        this.creditos = creditos;
        this.situacionService = situacionService;
        this.reloj = reloj;
    }

    // ----------------------------------------------------------------- cliente

    public List<CreditoResumen> listarDelCliente(Long clienteId) {
        LocalDate hoy = hoy();
        return creditos.findByClienteIdOrderByFechaDesembolsoDesc(clienteId).stream()
                .map(c -> resumen(c, hoy))
                .toList();
    }

    public CreditoDetalle detalleDelCliente(Long clienteId, Long creditoId) {
        return detalle(buscarDelCliente(clienteId, creditoId), hoy());
    }

    public PlanPagos planDelCliente(Long clienteId, Long creditoId) {
        return plan(buscarDelCliente(clienteId, creditoId), hoy());
    }

    // ----------------------------------------------------------------- administrador

    public List<CreditoResumen> listarTodos() {
        LocalDate hoy = hoy();
        return creditos.findAll().stream()
                .map(c -> resumen(c, hoy))
                .toList();
    }

    public CreditoDetalle detalle(Long creditoId) {
        return detalle(buscar(creditoId), hoy());
    }

    public PlanPagos plan(Long creditoId) {
        return plan(buscar(creditoId), hoy());
    }

    // ----------------------------------------------------------------- mapeo a DTO

    private CreditoResumen resumen(Credito c, LocalDate hoy) {
        SituacionCreditoService.Situacion s = situacionService.evaluar(c, c.getCuotas(), hoy);
        Cliente cl = c.getCliente();
        Vehiculo v = c.getVehiculo();
        ProximaCuota proxima = s.proximaCuota() == null ? null : new ProximaCuota(
                s.proximaCuota().getNumeroCuota(),
                s.proximaCuota().getFechaVencimiento(),
                s.proximaCuota().getSaldoPendiente(),
                s.diasParaProximaCuota());

        return new CreditoResumen(c.getId(), c.getNumeroCredito(),
                cl.getNombreCompleto(), cl.getNumeroDocumento(),
                v.getMarca() + " " + v.getLinea() + " " + v.getModelo(), v.getPlaca(),
                c.getValorFinanciado(), c.getPlazoMeses(), c.getTasaInteresMensual(), c.getValorCuota(),
                c.getSaldoCapital(), s.estado(), s.semaforo(), c.getEstadoPrenda(),
                s.cuotasPagadas(), s.cuotasPendientes(), s.cuotasVencidas(),
                s.valorVencido(), s.diasMora(), s.porcentajePagado(), proxima);
    }

    private CreditoDetalle detalle(Credito c, LocalDate hoy) {
        SituacionCreditoService.Situacion s = situacionService.evaluar(c, c.getCuotas(), hoy);
        Vehiculo v = c.getVehiculo();
        BigDecimal totalIntereses = c.getCuotas().stream().map(Cuota::getValorInteres).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPagar = c.getCuotas().stream().map(Cuota::getValorCuota).reduce(BigDecimal.ZERO, BigDecimal::add);
        VehiculoInfo vehiculo = new VehiculoInfo(v.getPlaca(), v.getMarca(), v.getLinea(), v.getModelo(),
                v.getColor(), v.getClase(), v.getNumeroMotor(), v.getNumeroChasis());

        return new CreditoDetalle(resumen(c, hoy), c.getValorVehiculo(), c.getCuotaInicial(),
                c.getFechaDesembolso(), c.getFechaPrimeraCuota(), c.getFechaCancelacion(),
                s.capitalPagado(), s.interesesPagados(), s.totalPagado(), totalIntereses, totalPagar, vehiculo);
    }

    private PlanPagos plan(Credito c, LocalDate hoy) {
        SituacionCreditoService.Situacion s = situacionService.evaluar(c, c.getCuotas(), hoy);
        List<CuotaInfo> filas = c.getCuotas().stream()
                .map(q -> new CuotaInfo(q.getNumeroCuota(), q.getFechaVencimiento(), q.getValorCapital(),
                        q.getValorInteres(), q.getValorCuota(), q.getSaldoCapitalFinal(), q.getValorPagado(),
                        q.getSaldoPendiente(), q.getFechaPago(),
                        situacionService.estadoVisible(q, hoy), situacionService.diasMora(q, hoy)))
                .toList();
        return new PlanPagos(c.getId(), c.getNumeroCredito(), hoy, filas.size(),
                s.cuotasPagadas(), s.cuotasVencidas(), s.valorVencido(), filas);
    }

    // ----------------------------------------------------------------- utilidades

    private Credito buscarDelCliente(Long clienteId, Long creditoId) {
        return creditos.findByIdAndClienteId(creditoId, clienteId)
                .orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado"));
    }

    private Credito buscar(Long creditoId) {
        return creditos.findById(creditoId)
                .orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado"));
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }
}
