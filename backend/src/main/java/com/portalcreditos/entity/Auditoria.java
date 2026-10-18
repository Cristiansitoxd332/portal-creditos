package com.portalcreditos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Bitacora de acciones relevantes. Guarda el username como texto (sin llave foranea)
 * para conservar el registro aunque el usuario sea eliminado.
 */
@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String username;

    @Column(nullable = false, length = 50)
    private String accion;

    @Column(length = 50)
    private String entidad;

    @Column(name = "entidad_id", length = 50)
    private String entidadId;

    @Column(length = 1000)
    private String detalle;

    @Column(name = "direccion_ip", length = 45)
    private String direccionIp;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;
}
