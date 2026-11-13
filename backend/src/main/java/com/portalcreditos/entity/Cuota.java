package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.EstadoCuota;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuota del plan de pagos (tabla de amortizacion) de un credito.
 */
@Entity
@Table(name = "cuota")
@Getter
@Setter
@NoArgsConstructor
public class Cuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credito_id", nullable = false)
    private Credito credito;

    @Column(name = "numero_cuota", nullable = false)
    private Integer numeroCuota;

    /** Seguro de vida de esta cuota (se paga antes que el interes). */
    @Column(name = "valor_seguro_vida", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorSeguroVida = BigDecimal.ZERO;

    /** FOGADE de esta cuota (se paga despues del seguro de vida y antes que el interes). */
    @Column(name = "valor_fogade", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorFogade = BigDecimal.ZERO;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "valor_capital", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorCapital;

    @Column(name = "valor_interes", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorInteres;

    @Column(name = "valor_cuota", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorCuota;

    @Column(name = "saldo_capital_final", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoCapitalFinal;

    @Column(name = "valor_pagado", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPagado = BigDecimal.ZERO;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuota estado = EstadoCuota.PENDIENTE;

    /** Seguro de vida + FOGADE de la cuota. */
    public BigDecimal getValorSeguros() {
        return valorSeguroVida.add(valorFogade);
    }

    /**
     * Capital ya cubierto en esta cuota. Orden dentro de la cuota: seguro de vida, FOGADE, interes y capital.
     */
    public BigDecimal getCapitalCubierto() {
        return valorPagado.subtract(getValorSeguros()).subtract(valorInteres)
                .max(BigDecimal.ZERO).min(valorCapital);
    }

    /** Interes ya cubierto en esta cuota. */
    public BigDecimal getInteresCubierto() {
        return valorPagado.subtract(getValorSeguros()).max(BigDecimal.ZERO).min(valorInteres);
    }

    /** Valor que falta por pagar de esta cuota. */
    public BigDecimal getSaldoPendiente() {
        return valorCuota.subtract(valorPagado);
    }

    /**
     * Una cuota esta vencida si no se ha pagado por completo y su fecha ya paso.
     * Se calcula en lugar de guardarse para que nunca quede desactualizada.
     */
    public boolean estaVencida(LocalDate hoy) {
        return estado != EstadoCuota.PAGADA && fechaVencimiento.isBefore(hoy);
    }
}
