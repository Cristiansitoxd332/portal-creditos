package com.portalcreditos.repository;

import com.portalcreditos.entity.MensajeCliente;
import com.portalcreditos.entity.enums.TipoMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Acceso a datos de los mensajes enviados a los clientes. */
public interface MensajeClienteRepository extends JpaRepository<MensajeCliente, Long> {

    List<MensajeCliente> findByClienteIdOrderByFechaEnvioDesc(Long clienteId);

    Optional<MensajeCliente> findByIdAndClienteId(Long id, Long clienteId);

    long countByClienteIdAndLeidoFalse(Long clienteId);

    List<MensajeCliente> findTop200ByOrderByFechaEnvioDesc();

    /** Para no repetir el mismo aviso el mismo dia. */
    boolean existsByCreditoIdAndTipoAndFechaEnvioGreaterThanEqual(Long creditoId, TipoMensaje tipo, LocalDateTime desde);
}
