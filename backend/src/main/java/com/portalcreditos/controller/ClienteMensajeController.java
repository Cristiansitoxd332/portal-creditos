package com.portalcreditos.controller;

import com.portalcreditos.dto.mensaje.MensajeDtos.BandejaCliente;
import com.portalcreditos.dto.mensaje.MensajeDtos.MensajeInfo;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.MensajeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Mensajes que la entidad le envio al cliente (solo rol CLIENTE, solo los propios). */
@RestController
@RequestMapping("/api/cliente/mensajes")
public class ClienteMensajeController {

    private final MensajeService mensajes;
    private final UsuarioActual usuario;

    public ClienteMensajeController(MensajeService mensajes, UsuarioActual usuario) {
        this.mensajes = mensajes;
        this.usuario = usuario;
    }

    @GetMapping
    public BandejaCliente bandeja() {
        return mensajes.bandeja(usuario.getClienteId());
    }

    /** Cantidad de mensajes sin leer (para el globo de notificaciones). */
    @GetMapping("/no-leidos")
    public Map<String, Long> noLeidos() {
        return Map.of("noLeidos", mensajes.noLeidos(usuario.getClienteId()));
    }

    @PostMapping("/{id}/leido")
    public MensajeInfo marcarLeido(@PathVariable Long id) {
        return mensajes.marcarLeido(usuario.getClienteId(), id);
    }
}
