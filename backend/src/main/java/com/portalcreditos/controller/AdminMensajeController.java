package com.portalcreditos.controller;

import com.portalcreditos.dto.mensaje.MensajeDtos.EnviarRequest;
import com.portalcreditos.dto.mensaje.MensajeDtos.EnviarTodosRequest;
import com.portalcreditos.dto.mensaje.MensajeDtos.MensajeInfo;
import com.portalcreditos.dto.mensaje.MensajeDtos.PanelAvisos;
import com.portalcreditos.dto.mensaje.MensajeDtos.ResultadoEnvio;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.MensajeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Recordatorios de pago y avisos de mora (solo rol ADMIN). */
@RestController
@RequestMapping("/api/admin/mensajes")
public class AdminMensajeController {

    private final MensajeService mensajes;
    private final UsuarioActual usuario;

    public AdminMensajeController(MensajeService mensajes, UsuarioActual usuario) {
        this.mensajes = mensajes;
        this.usuario = usuario;
    }

    /** Avisos que corresponden hoy (vence hoy, vence pronto, en mora) con el texto sugerido. */
    @GetMapping("/avisos")
    public PanelAvisos avisos() {
        return mensajes.panel();
    }

    /** Envia un mensaje a un cliente (queda en su portal). */
    @PostMapping
    public MensajeInfo enviar(@Valid @RequestBody EnviarRequest r, HttpServletRequest request) {
        return mensajes.enviar(r, usuario.getUsername(), request.getRemoteAddr());
    }

    /** Envia el aviso sugerido a todos los clientes de un grupo (VENCE_HOY, RECORDATORIO o MORA). */
    @PostMapping("/todos")
    public ResultadoEnvio enviarTodos(@Valid @RequestBody EnviarTodosRequest r, HttpServletRequest request) {
        return mensajes.enviarTodos(r.tipo(), usuario.getUsername(), request.getRemoteAddr());
    }

    /** Ultimos mensajes enviados. */
    @GetMapping
    public List<MensajeInfo> historial() {
        return mensajes.historial();
    }
}
