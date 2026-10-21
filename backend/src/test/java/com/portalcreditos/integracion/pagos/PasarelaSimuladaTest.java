package com.portalcreditos.integracion.pagos;

import com.portalcreditos.entity.enums.MedioPago;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Pruebas del adaptador de pasarela simulada. */
class PasarelaSimuladaTest {

    private final PasarelaSimulada pasarela = new PasarelaSimulada();

    @Test
    @DisplayName("Aprueba el pago y genera un codigo de autorizacion SIM-########")
    void apruebaPago() {
        PasarelaPago.ResultadoPasarela r = pasarela.procesar(new PasarelaPago.SolicitudPasarela(
                "PAG-TEST", new BigDecimal("100000"), MedioPago.PSE_SIMULADO, "prueba", false));

        assertEquals(true, r.aprobado());
        assertEquals(12, r.codigoAutorizacion().length());
        assertEquals("SIM-", r.codigoAutorizacion().substring(0, 4));
    }

    @Test
    @DisplayName("Simula un rechazo de la entidad financiera cuando se solicita")
    void simulaRechazo() {
        PasarelaPago.ResultadoPasarela r = pasarela.procesar(new PasarelaPago.SolicitudPasarela(
                "PAG-TEST", new BigDecimal("100000"), MedioPago.TARJETA_SIMULADA, "prueba", true));

        assertEquals(false, r.aprobado());
        assertNull(r.codigoAutorizacion());
    }
}
