package com.portalcreditos.service;

import com.portalcreditos.dto.auth.CambiarPasswordRequest;
import com.portalcreditos.dto.auth.LoginRequest;
import com.portalcreditos.dto.auth.LoginResponse;
import com.portalcreditos.dto.auth.PerfilResponse;
import com.portalcreditos.entity.Usuario;
import com.portalcreditos.exception.NegocioException;
import com.portalcreditos.repository.UsuarioRepository;
import com.portalcreditos.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Logica de autenticacion:
 * - Valida credenciales contra el hash BCrypt.
 * - Bloquea la cuenta tras N intentos fallidos consecutivos (por defecto 5).
 * - Usa un mensaje generico ante credenciales invalidas para no revelar si el usuario existe.
 * - Registra en auditoria los inicios de sesion exitosos y fallidos.
 */
@Service
public class AuthService {

    private static final String CREDENCIALES_INVALIDAS = "Usuario o contrasena incorrectos.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditoriaService auditoria;
    private final int maxIntentos;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditoriaService auditoria,
                       @Value("${app.seguridad.max-intentos-fallidos}") int maxIntentos) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditoria = auditoria;
        this.maxIntentos = maxIntentos;
    }

    /**
     * noRollbackFor: aunque se lance NegocioException, se confirma el aumento
     * del contador de intentos fallidos.
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public LoginResponse login(LoginRequest solicitud, String ip) {
        String username = solicitud.username().trim();
        Usuario usuario = usuarios.findByUsername(username).orElse(null);

        if (usuario == null) {
            auditoria.registrar(username, "LOGIN_FALLIDO", "usuario", null, "Usuario inexistente", ip);
            throw new NegocioException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        }
        if (!usuario.isActivo()) {
            auditoria.registrar(username, "LOGIN_RECHAZADO", "usuario", id(usuario), "Cuenta inactiva", ip);
            throw new NegocioException(HttpStatus.FORBIDDEN, "La cuenta esta inactiva. Comuniquese con la entidad.");
        }
        if (usuario.getIntentosFallidos() >= maxIntentos) {
            auditoria.registrar(username, "LOGIN_RECHAZADO", "usuario", id(usuario), "Cuenta bloqueada", ip);
            throw cuentaBloqueada();
        }
        if (!passwordEncoder.matches(solicitud.password(), usuario.getPasswordHash())) {
            usuario.setIntentosFallidos(usuario.getIntentosFallidos() + 1);
            boolean bloquear = usuario.getIntentosFallidos() >= maxIntentos;
            auditoria.registrar(username, bloquear ? "CUENTA_BLOQUEADA" : "LOGIN_FALLIDO", "usuario", id(usuario),
                    "Contrasena incorrecta (intento " + usuario.getIntentosFallidos() + " de " + maxIntentos + ")", ip);
            throw bloquear ? cuentaBloqueada() : new NegocioException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        }

        usuario.setIntentosFallidos(0);
        usuario.setUltimoAcceso(LocalDateTime.now());
        String nombre = nombreParaMostrar(usuario);
        JwtService.TokenGenerado token = jwtService.generar(usuario, nombre);
        auditoria.registrar(username, "LOGIN_EXITOSO", "usuario", id(usuario), null, ip);

        return new LoginResponse(token.token(), "Bearer", token.expiraEn(), usuario.getUsername(),
                usuario.getRol().name(), nombre, usuario.isDebeCambiarPassword());
    }

    @Transactional(readOnly = true)
    public PerfilResponse perfil(String username) {
        Usuario u = buscar(username);
        return new PerfilResponse(u.getUsername(), u.getRol().name(), nombreParaMostrar(u),
                u.getCliente() != null ? u.getCliente().getId() : null,
                u.getCliente() != null ? u.getCliente().getCorreo() : null,
                u.isDebeCambiarPassword(), u.getUltimoAcceso());
    }

    @Transactional
    public void cambiarPassword(String username, CambiarPasswordRequest solicitud, String ip) {
        Usuario u = buscar(username);
        if (!passwordEncoder.matches(solicitud.passwordActual(), u.getPasswordHash())) {
            throw NegocioException.solicitudInvalida("La contrasena actual no es correcta.");
        }
        if (!solicitud.passwordNueva().equals(solicitud.confirmacion())) {
            throw NegocioException.solicitudInvalida("La confirmacion no coincide con la nueva contrasena.");
        }
        if (passwordEncoder.matches(solicitud.passwordNueva(), u.getPasswordHash())) {
            throw NegocioException.solicitudInvalida("La nueva contrasena debe ser diferente a la actual.");
        }
        u.setPasswordHash(passwordEncoder.encode(solicitud.passwordNueva()));
        u.setDebeCambiarPassword(false);
        auditoria.registrar(username, "CAMBIO_PASSWORD", "usuario", id(u), null, ip);
    }

    private Usuario buscar(String username) {
        return usuarios.findByUsername(username)
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
    }

    private String nombreParaMostrar(Usuario u) {
        return u.getCliente() != null ? u.getCliente().getNombreCompleto() : "Administrador";
    }

    private NegocioException cuentaBloqueada() {
        return new NegocioException(HttpStatus.LOCKED,
                "La cuenta fue bloqueada por superar " + maxIntentos
                        + " intentos fallidos. Comuniquese con la entidad para desbloquearla.");
    }

    private static String id(Usuario u) {
        return String.valueOf(u.getId());
    }
}
