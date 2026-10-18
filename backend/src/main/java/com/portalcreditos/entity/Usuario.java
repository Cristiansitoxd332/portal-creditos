package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.Rol;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Cuenta de acceso. Un CLIENTE esta asociado a un registro de cliente; un ADMIN no.
 * La contrasena se almacena cifrada con BCrypt, nunca en texto plano.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", unique = true)
    private Cliente cliente;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword = true;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos = 0;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
