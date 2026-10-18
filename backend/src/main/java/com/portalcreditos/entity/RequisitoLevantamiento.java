package com.portalcreditos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Requisito informativo para solicitar el levantamiento de prenda (configurable por el admin).
 */
@Entity
@Table(name = "requisito_levantamiento")
@Getter
@Setter
@NoArgsConstructor
public class RequisitoLevantamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(nullable = false)
    private boolean activo = true;
}
