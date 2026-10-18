package com.portalcreditos.repository;

import com.portalcreditos.entity.ImportacionError;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Acceso a datos de la tabla de ImportacionError. Spring Data genera la implementacion automaticamente. */
public interface ImportacionErrorRepository extends JpaRepository<ImportacionError, Long> {

    List<ImportacionError> findByImportacionIdOrderByNumeroFilaAsc(Long importacionId);
}
