package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.EstadoSolicitud;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Solicitud (simulada) de levantamiento de prenda y su seguimiento.
 */
@Entity
@Table(name = "solicitud_levantamiento")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudLevantamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_radicado", nullable = false, unique = true, length = 30)
    private String numeroRadicado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credito_id", nullable = false)
    private Credito credito;

    @CreationTimestamp
    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.RADICADA;

    @Column(name = "observacion_cliente", length = 500)
    private String observacionCliente;

    @Column(name = "respuesta_admin", length = 500)
    private String respuestaAdmin;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendida_por")
    private Usuario atendidaPor;
}
