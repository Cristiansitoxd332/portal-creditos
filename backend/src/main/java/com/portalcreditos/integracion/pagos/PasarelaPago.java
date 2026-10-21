package com.portalcreditos.integracion.pagos;

import com.portalcreditos.entity.enums.MedioPago;

import java.math.BigDecimal;

/**
 * PATRON ADAPTADOR: contrato que debe cumplir cualquier pasarela de pagos.
 *
 * La logica de negocio (PagoService) solo conoce esta interfaz, no a un proveedor concreto.
 * - Hoy existe una unica implementacion: {@link PasarelaSimulada}, que NO mueve dinero real.
 * - En el futuro, con un contrato y credenciales de un proveedor autorizado, bastaria con crear
 *   otra implementacion (un nuevo adaptador) sin modificar el resto de la aplicacion.
 */
public interface PasarelaPago {

    /** Datos que se envian a la pasarela. Nunca incluye datos de tarjetas: el prototipo no los solicita. */
    record SolicitudPasarela(
            String referencia,
            BigDecimal valor,
            MedioPago medioPago,
            String descripcion,
            boolean simularRechazo
    ) {
    }

    /** Respuesta de la pasarela. */
    record ResultadoPasarela(
            boolean aprobado,
            String codigoAutorizacion,
            String mensaje
    ) {
    }

    ResultadoPasarela procesar(SolicitudPasarela solicitud);

    /** Nombre del proveedor, para mostrarlo en el comprobante. */
    String nombre();
}
