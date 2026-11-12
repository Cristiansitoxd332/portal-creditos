package com.portalcreditos.service;

import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCuota;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Calcula el cobro de mora por dias de atraso.
 *
 * Regla del prototipo: mientras el credito tenga al menos una cuota vencida sin pagar, se cobra un
 * valor fijo por cada dia de atraso (app.mora.valor-dia, por defecto $380), una sola vez por credito.
 * Los dias se cuentan desde el vencimiento de la cuota impaga mas antigua, o desde la fecha hasta la
 * que el cliente ya pago la mora (credito.moraCubiertaHasta), lo que sea mas reciente.
 *
 * Al pagar, el valor se aplica PRIMERO a la mora y el resto a las cuotas.
 * Es logica pura (sin base de datos), por eso tiene pruebas unitarias propias.
 */
@Component
public class CalculadoraMora {

    private final BigDecimal valorDia;

    public CalculadoraMora(@Value("${app.mora.valor-dia:380}") BigDecimal valorDia) {
        this.valorDia = valorDia.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal valorDia() {
        return valorDia;
    }

    /** Dias de atraso que aun no se han pagado. */
    public int diasPorCobrar(Credito credito, List<Cuota> cuotas, LocalDate hoy) {
        LocalDate inicio = inicio(credito, cuotas);
        if (inicio == null || !inicio.isBefore(hoy)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(inicio, hoy);
    }

    /** Valor de mora pendiente a la fecha. */
    public BigDecimal moraPendiente(Credito credito, List<Cuota> cuotas, LocalDate hoy) {
        return valorDia.multiply(BigDecimal.valueOf(diasPorCobrar(credito, cuotas, hoy)));
    }

    /**
     * Registra en el credito que se pago mora. Llamar ANTES de aplicar el resto del pago a las cuotas.
     * Si se pago toda la mora, queda cubierta hasta hoy; si fue parcial, avanza los dias completos pagados.
     */
    public void registrarPago(Credito credito, List<Cuota> cuotas, BigDecimal valorPagado, LocalDate hoy) {
        if (valorPagado == null || valorPagado.signum() <= 0) {
            return;
        }
        BigDecimal pendiente = moraPendiente(credito, cuotas, hoy);
        if (valorPagado.compareTo(pendiente) >= 0) {
            credito.setMoraCubiertaHasta(hoy);
            return;
        }
        long dias = valorDia.signum() == 0 ? 0 : valorPagado.divide(valorDia, 0, RoundingMode.DOWN).longValue();
        credito.setMoraCubiertaHasta(inicio(credito, cuotas).plusDays(dias));
    }

    /** Desde cuando corre la mora: vencimiento de la cuota impaga mas antigua o la mora ya cubierta. */
    private LocalDate inicio(Credito credito, List<Cuota> cuotas) {
        LocalDate masAntigua = cuotas.stream()
                .filter(c -> c.getEstado() != EstadoCuota.PAGADA)
                .map(Cuota::getFechaVencimiento)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (masAntigua == null) {
            return null;
        }
        LocalDate cubierta = credito.getMoraCubiertaHasta();
        return cubierta != null && cubierta.isAfter(masAntigua) ? cubierta : masAntigua;
    }
}
