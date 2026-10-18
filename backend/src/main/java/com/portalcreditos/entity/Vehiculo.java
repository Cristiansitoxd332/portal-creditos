package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.ClaseVehiculo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Vehiculo financiado y dado en prenda como garantia del credito.
 */
@Entity
@Table(name = "vehiculo")
@Getter
@Setter
@NoArgsConstructor
public class Vehiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String placa;

    @Column(nullable = false, length = 50)
    private String marca;

    @Column(nullable = false, length = 60)
    private String linea;

    /** Anio modelo del vehiculo. */
    @Column(nullable = false)
    private Short modelo;

    @Column(length = 30)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaseVehiculo clase;

    @Column(name = "numero_motor", length = 40)
    private String numeroMotor;

    @Column(name = "numero_chasis", length = 40)
    private String numeroChasis;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
