package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoPrenda;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Obligacion financiera de un cliente respaldada por un vehiculo.
 * saldoCapital y estado se recalculan en la capa de servicio con cada pago.
 */
@Entity
@Table(name = "credito")
@Getter
@Setter
@NoArgsConstructor
public class Credito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_credito", nullable = false, unique = true, length = 20)
    private String numeroCredito;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehiculo_id", nullable = false, unique = true)
    private Vehiculo vehiculo;

    @Column(name = "valor_vehiculo", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorVehiculo;

    @Column(name = "cuota_inicial", nullable = false, precision = 15, scale = 2)
    private BigDecimal cuotaInicial = BigDecimal.ZERO;

    @Column(name = "valor_financiado", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorFinanciado;

    /** Tasa mensual en porcentaje: 1.5000 significa 1.5 % mensual. */
    @Column(name = "tasa_interes_mensual", nullable = false, precision = 6, scale = 4)
    private BigDecimal tasaInteresMensual;

    @Column(name = "plazo_meses", nullable = false)
    private Integer plazoMeses;

    @Column(name = "valor_cuota", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorCuota;

    @Column(name = "fecha_desembolso", nullable = false)
    private LocalDate fechaDesembolso;

    @Column(name = "fecha_primera_cuota", nullable = false)
    private LocalDate fechaPrimeraCuota;

    @Column(name = "saldo_capital", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoCapital;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCredito estado = EstadoCredito.VIGENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_prenda", nullable = false, length = 20)
    private EstadoPrenda estadoPrenda = EstadoPrenda.VIGENTE;

    @Column(name = "fecha_cancelacion")
    private LocalDate fechaCancelacion;

    /** Fecha hasta la cual la mora diaria ya fue pagada (null si nunca se ha cobrado mora). */
    @Column(name = "mora_cubierta_hasta")
    private LocalDate moraCubiertaHasta;

    /** Plan de pagos ordenado por numero de cuota. */
    @OneToMany(mappedBy = "credito", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroCuota ASC")
    private List<Cuota> cuotas = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
