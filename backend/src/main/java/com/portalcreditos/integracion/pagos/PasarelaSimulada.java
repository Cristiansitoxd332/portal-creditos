package com.portalcreditos.integracion.pagos;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Adaptador SIMULADO de pasarela de pagos (unico disponible en el prototipo).
 *
 * No se conecta a ningun banco ni proveedor y no procesa dinero real.
 * Aprueba los pagos y genera un codigo de autorizacion ficticio. Si la solicitud pide
 * "simularRechazo", responde como si el banco hubiera rechazado la transaccion; esto permite
 * demostrar y probar el flujo de pagos rechazados.
 */
@Component
public class PasarelaSimulada implements PasarelaPago {

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public ResultadoPasarela procesar(SolicitudPasarela solicitud) {
        if (solicitud.simularRechazo()) {
            return new ResultadoPasarela(false, null,
                    "Transaccion rechazada por la entidad financiera (rechazo simulado).");
        }
        String codigo = "SIM-" + String.format("%08d", aleatorio.nextInt(100_000_000));
        return new ResultadoPasarela(true, codigo, "Transaccion aprobada (pago simulado, sin movimiento de dinero real).");
    }

    @Override
    public String nombre() {
        return "Pasarela simulada (prototipo)";
    }
}
