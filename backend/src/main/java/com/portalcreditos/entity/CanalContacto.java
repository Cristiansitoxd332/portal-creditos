package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.TipoCanal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Canal de atencion al cliente (configurable desde el panel administrativo).
 */
@Entity
@Table(name = "canal_contacto")
@Getter
@Setter
@NoArgsConstructor
public class CanalContacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCanal tipo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String valor;

    @Column(length = 300)
    private String enlace;

    @Column(length = 150)
    private String horario;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(nullable = false)
    private boolean activo = true;
}
