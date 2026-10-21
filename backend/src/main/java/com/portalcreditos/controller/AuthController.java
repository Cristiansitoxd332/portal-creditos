package com.portalcreditos.controller;

import com.portalcreditos.dto.auth.CambiarPasswordRequest;
import com.portalcreditos.dto.auth.LoginRequest;
import com.portalcreditos.dto.auth.LoginResponse;
import com.portalcreditos.dto.auth.PerfilResponse;
import com.portalcreditos.security.UsuarioActual;
import com.portalcreditos.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Endpoints de autenticacion: iniciar sesion, consultar el perfil y cambiar la contrasena. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UsuarioActual usuarioActual;

    public AuthController(AuthService authService, UsuarioActual usuarioActual) {
        this.authService = authService;
        this.usuarioActual = usuarioActual;
    }

    /** Publico: recibe usuario y contrasena, devuelve el token JWT. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest solicitud, HttpServletRequest request) {
        return authService.login(solicitud, request.getRemoteAddr());
    }

    /** Requiere token: datos del usuario autenticado. */
    @GetMapping("/perfil")
    public PerfilResponse perfil() {
        return authService.perfil(usuarioActual.getUsername());
    }

    /** Requiere token: cambia la contrasena del usuario autenticado. */
    @PostMapping("/cambiar-password")
    public ResponseEntity<Map<String, String>> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest solicitud,
                                                               HttpServletRequest request) {
        authService.cambiarPassword(usuarioActual.getUsername(), solicitud, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("mensaje", "Contrasena actualizada correctamente."));
    }
}
