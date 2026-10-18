package com.portalcreditos.repository;

import com.portalcreditos.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Acceso a datos de la tabla de Cliente. Spring Data genera la implementacion automaticamente. */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNumeroDocumento(String numeroDocumento);

    boolean existsByNumeroDocumento(String numeroDocumento);
}
