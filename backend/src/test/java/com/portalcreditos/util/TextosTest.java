package com.portalcreditos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pruebas de proteccion de datos personales y de seguridad de enlaces. */
class TextosTest {

    @Test
    @DisplayName("Enmascara el documento dejando visibles solo los ultimos 4 digitos")
    void enmascaraDocumento() {
        assertEquals("******0303", Textos.enmascararDocumento("1075200303"));
    }

    @Test
    @DisplayName("Enmascara el nombre dejando solo el primer nombre completo")
    void enmascaraNombre() {
        assertEquals("Diana P. V. S.", Textos.enmascararNombre("Diana Patricia", "Vargas Silva"));
    }

    @Test
    @DisplayName("Normaliza encabezados de Excel: sin tildes, minusculas y guion bajo")
    void normalizaEncabezados() {
        assertEquals("numero_documento", Textos.normalizarClave(" Número Documento "));
    }

    @Test
    @DisplayName("Solo acepta enlaces seguros (bloquea javascript:)")
    void enlacesSeguros() {
        assertEquals(true, Textos.enlaceSeguro("https://wa.me/573000000000"));
        assertEquals(true, Textos.enlaceSeguro("tel:+576080000000"));
        assertEquals(false, Textos.enlaceSeguro("javascript:alert(1)"));
    }

    @Test
    @DisplayName("La contrasena temporal cumple la politica de seguridad")
    void passwordTemporalCumplePolitica() {
        for (int i = 0; i < 50; i++) {
            String p = Textos.passwordTemporal();
            assertEquals(true, p.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$"));
        }
    }
}
