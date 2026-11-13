package com.portalcreditos.repository;

import com.portalcreditos.entity.Codeudor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acceso a datos de los codeudores (fiadores). */
public interface CodeudorRepository extends JpaRepository<Codeudor, Long> {

    Optional<Codeudor> findByNumeroDocumento(String numeroDocumento);
}
