package com.portalcreditos.repository;

import com.portalcreditos.entity.Credito;
import com.portalcreditos.entity.enums.EstadoCredito;
import com.portalcreditos.entity.enums.EstadoPrenda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    long countByClienteId(Long clienteId);

    boolean existsByNumeroCredito(String numeroCredito);

    /**
     * Obtiene el credito BLOQUEANDO su fila hasta que termine la transaccion (SELECT ... FOR UPDATE).
     * Evita que dos pagos simultaneos sobre el mismo credito se apliquen a la misma cuota.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Credito c where c.id = :id")
    Optional<Credito> bloquearPorId(@Param("id") Long id);

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
