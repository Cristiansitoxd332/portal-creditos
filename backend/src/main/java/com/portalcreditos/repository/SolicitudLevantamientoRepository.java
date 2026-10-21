package com.portalcreditos.repository;

import com.portalcreditos.entity.SolicitudLevantamiento;
import com.portalcreditos.entity.enums.EstadoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

/** Acceso a datos de la tabla de SolicitudLevantamiento. Spring Data genera la implementacion automaticamente. */
public interface SolicitudLevantamientoRepository extends JpaRepository<SolicitudLevantamiento, Long> {

    List<SolicitudLevantamiento> findByCreditoIdOrderByFechaSolicitudDesc(Long creditoId);

    List<SolicitudLevantamiento> findByEstadoOrderByFechaSolicitudAsc(EstadoSolicitud estado);

    boolean existsByCreditoIdAndEstadoIn(Long creditoId, Collection<EstadoSolicitud> estados);

    boolean existsByNumeroRadicado(String numeroRadicado);

    long countByEstadoIn(Collection<EstadoSolicitud> estados);
}
