package com.portalcreditos.repository;

import com.portalcreditos.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Acceso a datos de la tabla de Usuario. Spring Data genera la implementacion automaticamente. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByClienteId(Long clienteId);

    boolean existsByUsername(String username);
}
