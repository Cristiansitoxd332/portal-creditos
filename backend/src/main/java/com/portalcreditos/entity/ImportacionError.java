package com.portalcreditos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Error de validacion de una fila durante una importacion.
 */
@Entity
@Table(name = "importacion_error")
@Getter
@Setter
@NoArgsConstructor
public class ImportacionError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacion_id", nullable = false)
    private Importacion importacion;

    @Column(name = "numero_fila", nullable = false)
    private Integer numeroFila;

    @Column(length = 60)
    private String campo;

    @Column(nullable = false, length = 500)
    private String mensaje;
}
