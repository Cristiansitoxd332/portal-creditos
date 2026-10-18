package com.portalcreditos.repository;

import com.portalcreditos.entity.PazYSalvo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Acceso a datos de la tabla de PazYSalvo. Spring Data genera la implementacion automaticamente. */
public interface PazYSalvoRepository extends JpaRepository<PazYSalvo, Long> {

    Optional<PazYSalvo> findByCreditoId(Long creditoId);

    Optional<PazYSalvo> findByCodigoVerificacion(String codigoVerificacion);
}
