package com.portalcreditos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * Parte de un pago aplicada a una cuota especifica.
 * Resuelve la relacion muchos a muchos entre pagos y cuotas.
 */
@Entity
@Table(name = "aplicacion_pago")
@Getter
@Setter
@NoArgsConstructor
public class AplicacionPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuota_id", nullable = false)
    private Cuota cuota;

    @Column(name = "valor_aplicado", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorAplicado;
}
