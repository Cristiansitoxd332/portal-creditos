package com.portalcreditos.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de verificacion: confirma que la API esta en linea
 * y que la conexion con la base de datos MySQL funciona.
 */
@RestController
@RequestMapping("/api/salud")
public class SaludController {

    private final JdbcTemplate jdbcTemplate;

    public SaludController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, Object> verificar() {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("aplicacion", "Portal de Consulta de Creditos (prototipo con datos ficticios)");
        respuesta.put("fechaHora", LocalDateTime.now().toString());
        try {
            String baseDeDatos = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            respuesta.put("estado", "OK");
            respuesta.put("baseDeDatos", baseDeDatos);
            respuesta.put("versionMySQL", version);
        } catch (Exception e) {
            respuesta.put("estado", "ERROR");
            respuesta.put("detalle", e.getMessage());
        }
        return respuesta;
    }
}
