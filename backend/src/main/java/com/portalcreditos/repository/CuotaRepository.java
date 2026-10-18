package com.portalcreditos.repository;

import com.portalcreditos.entity.Cuota;
import com.portalcreditos.entity.enums.EstadoCuota;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Acceso a datos de la tabla de Cuota. Spring Data genera la implementacion automaticamente. */
public interface CuotaRepository extends JpaRepository<Cuota, Long> {

    List<Cuota> findByCreditoIdOrderByNumeroCuotaAsc(Long creditoId);

    /** Cuotas no pagadas por completo, en orden, para aplicar un pago a la mas antigua primero. */
    List<Cuota> findByCreditoIdAndEstadoNotOrderByNumeroCuotaAsc(Long creditoId, EstadoCuota estado);
}
