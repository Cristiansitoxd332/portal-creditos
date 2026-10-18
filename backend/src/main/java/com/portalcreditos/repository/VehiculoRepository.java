package com.portalcreditos.repository;

import com.portalcreditos.entity.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Acceso a datos de la tabla de Vehiculo. Spring Data genera la implementacion automaticamente. */
public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    Optional<Vehiculo> findByPlaca(String placa);

    boolean existsByPlaca(String placa);
}
