package com.portalcreditos.repository;

import com.portalcreditos.entity.RequisitoLevantamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Acceso a datos de la tabla de RequisitoLevantamiento. Spring Data genera la implementacion automaticamente. */
public interface RequisitoLevantamientoRepository extends JpaRepository<RequisitoLevantamiento, Long> {

    List<RequisitoLevantamiento> findByActivoTrueOrderByOrdenAsc();

    List<RequisitoLevantamiento> findAllByOrderByOrdenAsc();
}
