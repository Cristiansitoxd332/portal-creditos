package com.portalcreditos.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FechasDeCorteTest {

    @Test
    @DisplayName("Las cuotas solo pueden vencer los dias de corte 5 y 20")
    void soloDias5y20() {
        assertTrue(CreditoFactory.esFechaDeCorte(LocalDate.of(2026, 12, 5)));
        assertTrue(CreditoFactory.esFechaDeCorte(LocalDate.of(2026, 12, 20)));
        assertFalse(CreditoFactory.esFechaDeCorte(LocalDate.of(2026, 12, 11)));
    }

    @Test
    @DisplayName("La siguiente fecha de corte es el proximo 5 o 20 (o el 5 del mes siguiente)")
    void siguienteCorte() {
        assertEquals(LocalDate.of(2026, 12, 5), CreditoFactory.siguienteCorte(LocalDate.of(2026, 12, 1)));
        assertEquals(LocalDate.of(2026, 12, 20), CreditoFactory.siguienteCorte(LocalDate.of(2026, 12, 11)));
        assertEquals(LocalDate.of(2027, 1, 5), CreditoFactory.siguienteCorte(LocalDate.of(2026, 12, 21)));
        // desembolso 11/11/2026 -> 30 dias despues es 11/12/2026 -> primera cuota 20/12/2026
        assertEquals(LocalDate.of(2026, 12, 20), CreditoFactory.siguienteCorte(LocalDate.of(2026, 11, 11).plusDays(30)));
    }
}
