package com.portalcreditos.service.importacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pruebas de lectura de archivos CSV. */
class LectorArchivosTest {

    private final LectorArchivos lector = new LectorArchivos();

    private List<LectorArchivos.Fila> csv(String contenido) throws Exception {
        return lector.leerCsv(new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Detecta el separador punto y coma que usa Excel en espanol")
    void separadorPuntoYComa() throws Exception {
        List<LectorArchivos.Fila> filas = csv("Tipo Documento;Número Documento;Nombres\nCC;123456;Ana\n");
        assertEquals(1, filas.size());
        assertEquals("123456", filas.get(0).get("numero_documento"));
        assertEquals(2, filas.get(0).numero());
    }

    @Test
    @DisplayName("Respeta comas dentro de comillas y omite filas vacias")
    void comillasYFilasVacias() throws Exception {
        List<LectorArchivos.Fila> filas = csv("nombres,direccion\nAna,\"Calle 10, apto 2\"\n\nLuis,Carrera 5\n");
        assertEquals(2, filas.size());
        assertEquals("Calle 10, apto 2", filas.get(0).get("direccion"));
        assertEquals(4, filas.get(1).numero());
    }

    @Test
    @DisplayName("Ignora la marca BOM que agrega Excel al guardar CSV UTF-8")
    void ignoraBom() throws Exception {
        List<LectorArchivos.Fila> filas = csv("﻿nombres;ciudad\nAna;Neiva\n");
        assertEquals("Ana", filas.get(0).get("nombres"));
    }
}
