package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.TipoDocumento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Codeudor (fiador) que respalda uno o varios creditos. Un credito tiene como maximo un codeudor.
 * No tiene usuario en el portal: solo queda registrado para consulta y contacto de la entidad.
 */
@Entity
@Table(name = "codeudor")
@Getter
@Setter
@NoArgsConstructor
public class Codeudor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 5)
    private TipoDocumento tipoDocumento = TipoDocumento.CC;

    @Column(name = "numero_documento", nullable = false, unique = true, length = 20)
    private String numeroDocumento;

    @Column(nullable = false, length = 80)
    private String nombres;

    @Column(nullable = false, length = 80)
    private String apellidos;

    @Column(length = 20)
    private String telefono;

    @Column(length = 120)
    private String correo;

    @Column(length = 150)
    private String direccion;

    @Column(length = 60)
    private String ciudad;

    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }
}
