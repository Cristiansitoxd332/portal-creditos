package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.TipoMensaje;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Mensaje que la entidad envia a un cliente desde el panel administrativo (recordatorio de pago,
 * aviso de mora o mensaje libre). El cliente lo ve en su portal, en la seccion "Mensajes".
 */
@Entity
@Table(name = "mensaje_cliente")
@Getter
@Setter
@NoArgsConstructor
public class MensajeCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credito_id")
    private Credito credito;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMensaje tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 700)
    private String texto;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(name = "enviado_por", nullable = false, length = 50)
    private String enviadoPor;

    @Column(nullable = false)
    private boolean leido;

    @Column(name = "fecha_lectura")
    private LocalDateTime fechaLectura;
}
