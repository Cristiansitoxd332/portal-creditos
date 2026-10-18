package com.portalcreditos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Certificado de paz y salvo emitido (demostrativo, sin validez legal).
 * El codigo de verificacion permite comprobar publicamente que el certificado existe.
 */
@Entity
@Table(name = "paz_y_salvo")
@Getter
@Setter
@NoArgsConstructor
public class PazYSalvo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credito_id", nullable = false, unique = true)
    private Credito credito;

    @Column(name = "codigo_verificacion", nullable = false, unique = true, length = 40)
    private String codigoVerificacion;

    @CreationTimestamp
    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private LocalDateTime fechaEmision;
}
