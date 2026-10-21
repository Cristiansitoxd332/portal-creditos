package com.portalcreditos.service;

import com.portalcreditos.dto.simulador.SimuladorDtos.Escenario;
import com.portalcreditos.dto.simulador.SimuladorDtos.SimulacionAbono;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCuota;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.CreditoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * SIMULADOR DE ABONO EXTRAORDINARIO A CAPITAL.
 *
 * El cliente escribe un valor y ve, antes de decidir, que pasaria si lo abona a capital:
 * - REDUCIR PLAZO: mantiene la cuota y termina de pagar antes (normalmente ahorra mas intereses).
 * - REDUCIR CUOTA: mantiene el numero de cuotas y paga una cuota mas baja.
 *
 * Solo simula: no modifica el credito. Los tres escenarios (sin abono, reducir plazo y reducir cuota)
 * se calculan con el mismo metodo (sistema frances desde el saldo actual) para que la comparacion sea justa.
 */
@Service
public class SimuladorAbonoService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final String AVISO = "Simulación informativa. Un abono real a capital debe gestionarse "
            + "con la entidad a través de sus canales oficiales.";

    private final CreditoRepository creditos;
    private final AmortizacionService amortizacion;
    private final DistribuidorPagos distribuidor;
    private final Clock reloj;

    public SimuladorAbonoService(CreditoRepository creditos, AmortizacionService amortizacion,
                                 DistribuidorPagos distribuidor, Clock reloj) {
        this.creditos = creditos;
        this.amortizacion = amortizacion;
        this.distribuidor = distribuidor;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public SimulacionAbono simularDelCliente(Long clienteId, Long creditoId, BigDecimal valorAbono) {
        Credito c = creditos.findByIdAndClienteId(creditoId, clienteId)
                .orElseThrow(() -> NegocioException.noEncontrado("Credito no encontrado"));
        return simular(c, valorAbono, LocalDate.now(reloj));
    }

    public SimulacionAbono simular(Credito credito, BigDecimal valorAbono, LocalDate hoy) {
        List<Cuota> pendientes = credito.getCuotas().stream()
                .filter(q -> q.getEstado() != EstadoCuota.PAGADA)
                .sorted(Comparator.comparing(Cuota::getNumeroCuota))
                .toList();
        if (pendientes.isEmpty()) {
            throw NegocioException.conflicto("El crédito ya está cancelado; no hay saldo para abonar.");
        }
        BigDecimal saldo = distribuidor.saldoCapital(credito.getValorFinanciado(), credito.getCuotas());
        BigDecimal abono = valorAbono.setScale(2, RoundingMode.HALF_UP);
        if (abono.signum() <= 0) {
            throw NegocioException.solicitudInvalida("El abono debe ser mayor que cero.");
        }
        if (abono.compareTo(saldo) > 0) {
            throw NegocioException.solicitudInvalida("El abono no puede superar el saldo de capital ("
                    + DocumentoPdfService.pesos(saldo) + ").");
        }

        BigDecimal tasa = credito.getTasaInteresMensual();
        int restantes = pendientes.size();
        LocalDate primeraFecha = pendientes.get(0).getFechaVencimiento();
        BigDecimal nuevoSaldo = saldo.subtract(abono);

        Escenario sinAbono = conCuotasFijas(saldo, tasa, restantes, primeraFecha, null, 0);

        Escenario reducirPlazo;
        Escenario reducirCuota;
        if (nuevoSaldo.signum() == 0) {
            reducirPlazo = new Escenario(BigDecimal.ZERO.setScale(2), 0, BigDecimal.ZERO.setScale(2), null,
                    sinAbono.totalIntereses(), restantes);
            reducirCuota = reducirPlazo;
        } else {
            reducirPlazo = mismaCuotaMenosPlazo(nuevoSaldo, tasa, sinAbono.valorCuota(), primeraFecha,
                    sinAbono.totalIntereses(), restantes);
            reducirCuota = conCuotasFijas(nuevoSaldo, tasa, restantes, primeraFecha, sinAbono.totalIntereses(), restantes);
        }

        String recomendacion = reducirPlazo.ahorroIntereses().compareTo(reducirCuota.ahorroIntereses()) >= 0
                ? "Reducir el plazo le permite ahorrar más en intereses y terminar antes."
                : "Reducir la cuota le permite ahorrar más en intereses.";

        return new SimulacionAbono(credito.getNumeroCredito(), saldo, abono, nuevoSaldo, tasa,
                sinAbono, reducirPlazo, reducirCuota, recomendacion, AVISO);
    }

    /** Plan con cuota fija recalculada (sistema frances) para el saldo y numero de cuotas dados. */
    private Escenario conCuotasFijas(BigDecimal saldo, BigDecimal tasa, int cuotas, LocalDate primeraFecha,
                                     BigDecimal interesesReferencia, int cuotasReferencia) {
        AmortizacionService.PlanAmortizacion plan = amortizacion.calcular(saldo, tasa, cuotas, primeraFecha);
        BigDecimal ahorro = interesesReferencia == null ? BigDecimal.ZERO.setScale(2)
                : interesesReferencia.subtract(plan.totalIntereses());
        return new Escenario(plan.valorCuota(), cuotas, plan.totalIntereses(),
                plan.filas().get(plan.filas().size() - 1).fechaVencimiento(), ahorro, cuotasReferencia - cuotas);
    }

    /** Mantiene la cuota actual y cuenta cuantos meses se necesitan para pagar el nuevo saldo. */
    private Escenario mismaCuotaMenosPlazo(BigDecimal saldo, BigDecimal tasa, BigDecimal cuota, LocalDate primeraFecha,
                                           BigDecimal interesesReferencia, int cuotasReferencia) {
        BigDecimal i = tasa.divide(BigDecimal.valueOf(100), MC);
        BigDecimal pendiente = saldo;
        BigDecimal intereses = BigDecimal.ZERO;
        int meses = 0;
        while (pendiente.signum() > 0 && meses < 600) {
            BigDecimal interes = pendiente.multiply(i, MC).setScale(0, RoundingMode.HALF_UP);
            BigDecimal capital = cuota.subtract(interes);
            if (capital.signum() <= 0) {
                throw new IllegalStateException("La cuota no alcanza a cubrir los intereses");
            }
            intereses = intereses.add(interes);
            pendiente = pendiente.subtract(capital.min(pendiente));
            meses++;
        }
        BigDecimal totalIntereses = intereses.setScale(2, RoundingMode.HALF_UP);
        return new Escenario(cuota, meses, totalIntereses, primeraFecha.plusMonths(meses - 1L),
                interesesReferencia.subtract(totalIntereses), cuotasReferencia - meses);
    }
}
