package com.portalcreditos.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SucursalesTest {

    @Test
    void codigosDeSucursal() {
        assertEquals("01", Sucursales.normalizar(null));
        assertEquals("02", Sucursales.normalizar("2"));
        assertEquals("01 NEIVA", Sucursales.etiqueta("01"));
        assertEquals("Guadalupe", Sucursales.nombre("14"));
        assertTrue(Sucursales.existe("10"));
        assertFalse(Sucursales.existe("15"));
        assertEquals(14, Sucursales.todas().size());
    }
}
