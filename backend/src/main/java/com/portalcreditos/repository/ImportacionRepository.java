package com.portalcreditos.repository;

import com.portalcreditos.entity.Importacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Acceso a datos de la tabla de Importacion. Spring Data genera la implementacion automaticamente. */
public interface ImportacionRepository extends JpaRepository<Importacion, Long> {

    List<Importacion> findAllByOrderByFechaCargaDesc();
}
