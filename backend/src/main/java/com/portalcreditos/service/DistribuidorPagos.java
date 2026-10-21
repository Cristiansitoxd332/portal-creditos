package com.portalcreditos.service;

import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCuota;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reglas para aplicar un pago a las cuotas de un credito:
 *
 * 1. El pago se aplica primero a la cuota MAS ANTIGUA que tenga saldo (primero lo vencido).
 * 2. Si el valor no alcanza para la cuota completa, esta queda en estado PARCIAL.
 * 3. Dentro de cada cuota, lo pagado cubre primero el interes y luego el capital.
 * 4. No se permite pagar mas de lo que se debe en el plan.
 *
 * Es logica pura (sin base de datos), por eso tiene pruebas unitarias propias.
 */
@Component
public class DistribuidorPagos {

    /** Cuanto se aplico a cada cuota. */
    public record Aplicacion(Cuota cuota, BigDecimal valorAplicado) {
    }

    /** Suma de lo que falta por pagar en todas las cuotas. */
    public BigDecimal totalPendiente(List<Cuota> cuotas) {
        return cuotas.stream().map(Cuota::getSaldoPendiente).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Aplica el valor a las cuotas (las modifica) y devuelve el detalle de la aplicacion.
     * @throws IllegalArgumentException si el valor es cero, negativo o supera lo pendiente
     */
    public List<Aplicacion> distribuir(List<Cuota> cuotas, BigDecimal valor, LocalDate fechaPago) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("El valor del pago debe ser mayor que cero");
        }
        if (valor.compareTo(totalPendiente(cuotas)) > 0) {
            throw new IllegalArgumentException("El valor del pago supera el saldo pendiente del credito");
        }

        List<Cuota> ordenadas = cuotas.stream()
                .filter(c -> c.getEstado() != EstadoCuota.PAGADA)
                .sorted(Comparator.comparing(Cuota::getNumeroCuota))
                .toList();

        List<Aplicacion> aplicaciones = new ArrayList<>();
        BigDecimal restante = valor;
        for (Cuota c : ordenadas) {
            if (restante.signum() == 0) {
                break;
            }
            BigDecimal aplicar = restante.min(c.getSaldoPendiente());
            c.setValorPagado(c.getValorPagado().add(aplicar));
            if (c.getSaldoPendiente().signum() == 0) {
                c.setEstado(EstadoCuota.PAGADA);
                c.setFechaPago(fechaPago);
            } else {
                c.setEstado(EstadoCuota.PARCIAL);
            }
            aplicaciones.add(new Aplicacion(c, aplicar));
            restante = restante.subtract(aplicar);
        }
        return aplicaciones;
    }

    /**
     * Saldo de capital del credito = valor financiado - capital ya cubierto en las cuotas.
     * En cada cuota, lo pagado cubre primero el interes y el excedente abona a capital.
     */
    public BigDecimal saldoCapital(BigDecimal valorFinanciado, List<Cuota> cuotas) {
        BigDecimal capitalCubierto = BigDecimal.ZERO;
        for (Cuota c : cuotas) {
            BigDecimal sobreInteres = c.getValorPagado().subtract(c.getValorInteres()).max(BigDecimal.ZERO);
            capitalCubierto = capitalCubierto.add(sobreInteres.min(c.getValorCapital()));
        }
        return valorFinanciado.subtract(capitalCubierto).max(BigDecimal.ZERO);
    }
}
