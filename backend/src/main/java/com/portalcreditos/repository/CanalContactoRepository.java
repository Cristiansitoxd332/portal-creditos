package com.portalcreditos.repository;

import com.portalcreditos.entity.CanalContacto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Acceso a datos de la tabla de CanalContacto. Spring Data genera la implementacion automaticamente. */
public interface CanalContactoRepository extends JpaRepository<CanalContacto, Long> {

    List<CanalContacto> findByActivoTrueOrderByOrdenAsc();

    List<CanalContacto> findAllByOrderByOrdenAsc();
}
