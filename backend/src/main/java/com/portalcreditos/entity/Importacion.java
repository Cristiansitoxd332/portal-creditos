package com.portalcreditos.entity;

import com.portalcreditos.entity.enums.EstadoImportacion;
import com.portalcreditos.entity.enums.TipoCarga;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro historico de una carga de archivo Excel/CSV.
 */
@Entity
@Table(name = "importacion")
@Getter
@Setter
@NoArgsConstructor
public class Importacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_carga", nullable = false, length = 20)
    private TipoCarga tipoCarga;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @CreationTimestamp
    @Column(name = "fecha_carga", nullable = false, updatable = false)
    private LocalDateTime fechaCarga;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "total_filas", nullable = false)
    private int totalFilas;

    @Column(name = "filas_exitosas", nullable = false)
    private int filasExitosas;

    @Column(name = "filas_con_error", nullable = false)
    private int filasConError;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoImportacion estado = EstadoImportacion.PROCESANDO;

    @OneToMany(mappedBy = "importacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroFila ASC")
    private List<ImportacionError> errores = new ArrayList<>();
}
