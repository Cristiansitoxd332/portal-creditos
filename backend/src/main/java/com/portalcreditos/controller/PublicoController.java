package com.portalcreditos.controller;

import com.portalcreditos.dto.configuracion.ConfiguracionDtos.CanalInfo;
import com.portalcreditos.dto.configuracion.ConfiguracionDtos.RequisitoInfo;
import com.portalcreditos.dto.documento.DocumentoDtos.VerificacionPazYSalvo;
import com.portalcreditos.service.ConfiguracionService;
import com.portalcreditos.service.PazYSalvoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints PUBLICOS (no requieren iniciar sesion). */
@RestController
@RequestMapping("/api/publico")
public class PublicoController {

    private final ConfiguracionService configuracion;
    private final PazYSalvoService pazYSalvo;

    public PublicoController(ConfiguracionService configuracion, PazYSalvoService pazYSalvo) {
        this.configuracion = configuracion;
        this.pazYSalvo = pazYSalvo;
    }

    /** Canales de atencion activos (WhatsApp, telefono, correo, oficinas...). */
    @GetMapping("/canales")
    public List<CanalInfo> canales() {
        return configuracion.canalesActivos();
    }

    /** Requisitos para solicitar el levantamiento de prenda. */
    @GetMapping("/requisitos-levantamiento")
    public List<RequisitoInfo> requisitos() {
        return configuracion.requisitosActivos();
    }

    /** Verificacion de un paz y salvo por su codigo (el que aparece en el QR del documento). */
    @GetMapping("/paz-y-salvo/{codigo}")
    public VerificacionPazYSalvo verificar(@PathVariable String codigo) {
        return pazYSalvo.verificar(codigo);
    }
}
