package com.portalcreditos.service;

import com.portalcreditos.dto.credito.EstadoVisibleCuota;
import com.portalcreditos.dto.credito.Semaforo;
import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoCuota;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Evalua la situacion de un credito en una fecha dada: cuotas pagadas, pendientes y vencidas,
 * valor en mora, dias de mora, proxima cuota, porcentaje pagado y semaforo de estado.
 *
 * La situacion se CALCULA a partir de las cuotas y la fecha, en lugar de confiar en un estado
 * guardado, para que siempre este actualizada. Es logica pura: no consulta la base de datos.
 */
@Service
public class SituacionCreditoService {

    private final int diasAlertaVencimiento;

    public SituacionCreditoService(@Value("${app.dias-alerta-vencimiento:5}") int diasAlertaVencimiento) {
        this.diasAlertaVencimiento = diasAlertaVencimiento;
    }

    /** Resultado de la evaluacion. */
    public record Situacion(
            EstadoCredito estado,
            Semaforo semaforo,
            int cuotasPagadas,
            int cuotasPendientes,
            int cuotasVencidas,
            BigDecimal valorVencido,
            int diasMora,
            BigDecimal capitalPagado,
            BigDecimal interesesPagados,
            BigDecimal totalPagado,
            BigDecimal porcentajePagado,
            Cuota proximaCuota,
            Integer diasParaProximaCuota
    ) {
    }

    public Situacion evaluar(Credito credito, List<Cuota> cuotas, LocalDate hoy) {
        int pagadas = 0;
        int vencidas = 0;
        BigDecimal valorVencido = BigDecimal.ZERO;
        BigDecimal capitalPagado = BigDecimal.ZERO;
        BigDecimal interesesPagados = BigDecimal.ZERO;
        BigDecimal totalPagado = BigDecimal.ZERO;
        LocalDate vencimientoMasAntiguo = null;

        for (Cuota c : cuotas) {
            BigDecimal pagado = c.getValorPagado();
            totalPagado = totalPagado.add(pagado);
            // Cada pago cubre primero el interes de la cuota y luego el capital
            BigDecimal interesCubierto = pagado.min(c.getValorInteres());
            interesesPagados = interesesPagados.add(interesCubierto);
            capitalPagado = capitalPagado.add(pagado.subtract(interesCubierto).min(c.getValorCapital()));

            if (c.getEstado() == EstadoCuota.PAGADA) {
                pagadas++;
            } else if (c.estaVencida(hoy)) {
                vencidas++;
                valorVencido = valorVencido.add(c.getSaldoPendiente());
                if (vencimientoMasAntiguo == null || c.getFechaVencimiento().isBefore(vencimientoMasAntiguo)) {
                    vencimientoMasAntiguo = c.getFechaVencimiento();
                }
            }
        }

        Optional<Cuota> proxima = cuotas.stream()
                .filter(c -> c.getEstado() != EstadoCuota.PAGADA)
                .filter(c -> !c.getFechaVencimiento().isBefore(hoy))
                .min(Comparator.comparing(Cuota::getFechaVencimiento));

        int diasMora = vencimientoMasAntiguo == null ? 0 : (int) ChronoUnit.DAYS.between(vencimientoMasAntiguo, hoy);
        Integer diasParaProxima = proxima.map(c -> (int) ChronoUnit.DAYS.between(hoy, c.getFechaVencimiento())).orElse(null);

        boolean cancelado = !cuotas.isEmpty() && pagadas == cuotas.size();
        EstadoCredito estado = cancelado ? EstadoCredito.CANCELADO
                : vencidas > 0 ? EstadoCredito.EN_MORA
                : EstadoCredito.VIGENTE;

        Semaforo semaforo = switch (estado) {
            case CANCELADO -> Semaforo.CANCELADO;
            case EN_MORA -> Semaforo.EN_MORA;
            case VIGENTE -> diasParaProxima != null && diasParaProxima <= diasAlertaVencimiento
                    ? Semaforo.PROXIMA_A_VENCER : Semaforo.AL_DIA;
        };

        BigDecimal porcentaje = credito.getValorFinanciado().signum() == 0 ? BigDecimal.ZERO
                : capitalPagado.multiply(BigDecimal.valueOf(100))
                .divide(credito.getValorFinanciado(), 1, RoundingMode.HALF_UP);

        return new Situacion(estado, semaforo, pagadas, cuotas.size() - pagadas, vencidas,
                valorVencido, diasMora, capitalPagado, interesesPagados, totalPagado, porcentaje,
                proxima.orElse(null), diasParaProxima);
    }

    /** Estado que se muestra al usuario para una cuota (incluye VENCIDA, que no se guarda). */
    public EstadoVisibleCuota estadoVisible(Cuota cuota, LocalDate hoy) {
        if (cuota.getEstado() == EstadoCuota.PAGADA) {
            return EstadoVisibleCuota.PAGADA;
        }
        if (cuota.estaVencida(hoy)) {
            return EstadoVisibleCuota.VENCIDA;
        }
        return cuota.getEstado() == EstadoCuota.PARCIAL ? EstadoVisibleCuota.PARCIAL : EstadoVisibleCuota.PENDIENTE;
    }

    /** Dias de mora de una cuota (0 si no esta vencida). */
    public int diasMora(Cuota cuota, LocalDate hoy) {
        return cuota.estaVencida(hoy) ? (int) ChronoUnit.DAYS.between(cuota.getFechaVencimiento(), hoy) : 0;
    }
}
