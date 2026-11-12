package com.portalcreditos.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rentar maneja dos tasas: 2,84 % general y 1,80 % para empleados de Rentar o Yamaha. */
class TasasRentarTest {

    @Test
    void aceptaSoloLasDosTasasDeRentar() {
        assertTrue(CreditoFactory.esTasaPermitida(new BigDecimal("2.84")));
        assertTrue(CreditoFactory.esTasaPermitida(new BigDecimal("2.8400")));
        assertTrue(CreditoFactory.esTasaPermitida(new BigDecimal("1.8")));
        assertFalse(CreditoFactory.esTasaPermitida(new BigDecimal("2.0")));
        assertFalse(CreditoFactory.esTasaPermitida(new BigDecimal("1.85")));
        assertFalse(CreditoFactory.esTasaPermitida(null));
    }
}
