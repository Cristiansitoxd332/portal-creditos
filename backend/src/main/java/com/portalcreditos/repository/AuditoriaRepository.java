package com.portalcreditos.repository;

import com.portalcreditos.entity.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a datos de la tabla de Auditoria. Spring Data genera la implementacion automaticamente. */
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    Page<Auditoria> findAllByOrderByFechaDesc(Pageable pageable);
}
