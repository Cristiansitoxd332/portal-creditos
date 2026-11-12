package com.portalcreditos.service;

import com.portalcreditos.dto.credito.CreditoDtos.CreditoResumen;
import com.portalcreditos.dto.credito.CreditoDtos.ProximaCuota;
import com.portalcreditos.entity.enums.TipoMensaje;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de los avisos: mora, vence hoy (dia de corte) y recordatorio de 1 a 3 dias antes. */
class MensajeServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);

    private static CreditoResumen resumen(int vencidas, LocalDate proxima) {
        return new CreditoResumen(1L, "CV-1", "Ana", "1", "Yamaha", "ABC12D", BigDecimal.TEN, 12, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.TEN, null, null, null, 0, 12, vencidas, BigDecimal.ZERO, vencidas * 10,
                BigDecimal.ZERO, new BigDecimal("380"), BigDecimal.ZERO,
                proxima == null ? null : new ProximaCuota(1, proxima, new BigDecimal("670753"), 0));
    }

    @Test
    void clasificaLosAvisos() {
        assertEquals(TipoMensaje.MORA, MensajeService.clasificar(resumen(2, HOY), HOY));
        assertEquals(TipoMensaje.VENCE_HOY, MensajeService.clasificar(resumen(0, HOY), HOY));
        assertEquals(TipoMensaje.RECORDATORIO, MensajeService.clasificar(resumen(0, HOY.plusDays(3)), HOY));
        assertNull(MensajeService.clasificar(resumen(0, HOY.plusDays(4)), HOY));
        assertNull(MensajeService.clasificar(resumen(0, null), HOY));
    }

    @Test
    void armaElEnlaceDeWhatsappYLosPesos() {
        assertEquals("$670.753", MensajeService.pesos(new BigDecimal("670753.00")));
        String enlace = MensajeService.enlaceWhatsapp("300 000 0101", "Hola Ana");
        assertEquals("https://wa.me/573000000101?text=Hola%20Ana", enlace);
        assertNull(MensajeService.enlaceWhatsapp("123", "x"));
        assertTrue(MensajeService.titulo(TipoMensaje.MORA).contains("mora"));
    }
}
