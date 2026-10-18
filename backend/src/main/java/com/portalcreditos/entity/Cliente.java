package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.TipoDocumento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Persona titular de uno o varios creditos.
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 5)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false, unique = true, length = 20)
    private String numeroDocumento;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(length = 150)
    private String correo;

    @Column(length = 20)
    private String telefono;

    @Column(length = 200)
    private String direccion;

    @Column(length = 80)
    private String ciudad;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /** Nombre completo para mostrar en pantallas y documentos. */
    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }
}
