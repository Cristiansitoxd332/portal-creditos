package com.portalcreditos.repository;

import com.portalcreditos.entity.AplicacionPago;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Acceso a datos de la tabla de AplicacionPago. Spring Data genera la implementacion automaticamente. */
public interface AplicacionPagoRepository extends JpaRepository<AplicacionPago, Long> {

    List<AplicacionPago> findByPagoId(Long pagoId);

    List<AplicacionPago> findByCuotaId(Long cuotaId);
}
