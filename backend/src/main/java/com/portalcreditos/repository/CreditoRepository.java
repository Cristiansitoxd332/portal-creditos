package com.portalcreditos.repository;

import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoPrenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Acceso a datos de la tabla de Credito. Spring Data genera la implementacion automaticamente. */
public interface CreditoRepository extends JpaRepository<Credito, Long> {

    Optional<Credito> findByNumeroCredito(String numeroCredito);

    List<Credito> findByClienteIdOrderByFechaDesembolsoDesc(Long clienteId);

    /** Valida que un credito pertenezca al cliente (evita que un cliente vea creditos ajenos). */
    Optional<Credito> findByIdAndClienteId(Long id, Long clienteId);

    long countByEstado(EstadoCredito estado);

    /** Resumen liviano de todos los creditos (proyeccion: solo trae las columnas necesarias). */
    @Query("""
            select c.id as id,
                   c.numeroCredito as numeroCredito,
                   concat(cl.nombres, ' ', cl.apellidos) as cliente,
                   c.estado as estado,
                   c.estadoPrenda as estadoPrenda,
                   c.saldoCapital as saldoCapital
            from Credito c
            join c.cliente cl
            order by c.id
            """)
    List<ResumenCreditoView> listarResumen();

    interface ResumenCreditoView {
        Long getId();
        String getNumeroCredito();
        String getCliente();
        EstadoCredito getEstado();
        EstadoPrenda getEstadoPrenda();
        BigDecimal getSaldoCapital();
    }
}
