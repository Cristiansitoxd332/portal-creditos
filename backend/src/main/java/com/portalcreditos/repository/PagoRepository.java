package com.portalcreditos.repository;

import com.portalcreditos.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Acceso a datos de la tabla de Pago. Spring Data genera la implementacion automaticamente. */
public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByCreditoIdOrderByFechaPagoDesc(Long creditoId);

    boolean existsByReferencia(String referencia);
}
