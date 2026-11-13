package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.CanalPago;
import com.portalcreditos.entity.enums.EstadoPago;
import com.portalcreditos.entity.enums.MedioPago;
import com.portalcreditos.entity.enums.TipoAbono;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaccion de pago registrada. En el prototipo todos los pagos son simulados.
 */
@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credito_id", nullable = false)
    private Credito credito;

    @Column(nullable = false, unique = true, length = 40)
    private String referencia;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    /** Parte del pago que se aplico a la mora por dias de atraso (el resto va a las cuotas). */
    @Column(name = "valor_mora", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorMora = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 30)
    private MedioPago medioPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalPago canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado = EstadoPago.APROBADO;

    @Column(length = 255)
    private String observacion;

    /** Sucursal donde se recibio el pago (abonos del administrador). */
    @Column(length = 3)
    private String sucursal;

    /** Tipo de abono (solo para abonos registrados por el administrador; null en pagos del portal). */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_abono", length = 20)
    private TipoAbono tipoAbono;

    /** Parte del pago que se abono directamente a capital (abonos extraordinarios). */
    @Column(name = "valor_abono_capital", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorAbonoCapital = BigDecimal.ZERO;

    /** Usuario administrador que registro el pago (null si lo hizo el cliente). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AplicacionPago> aplicaciones = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}
