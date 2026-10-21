package com.portalcreditos.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Construye respuestas de descarga de archivos (PDF, Excel). */
final class Descargas {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private Descargas() {
    }

    static ResponseEntity<byte[]> pdf(byte[] contenido, String nombreArchivo) {
        return archivo(contenido, nombreArchivo, MediaType.APPLICATION_PDF);
    }

    static ResponseEntity<byte[]> excel(byte[] contenido, String nombreArchivo) {
        return archivo(contenido, nombreArchivo, XLSX);
    }

    private static ResponseEntity<byte[]> archivo(byte[] contenido, String nombreArchivo, MediaType tipo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .contentType(tipo)
                .body(contenido);
    }
}
