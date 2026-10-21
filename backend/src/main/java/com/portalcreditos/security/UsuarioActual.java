package com.portalcreditos.security;

import com.portalcreditos.exception.NegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Da acceso a los datos del usuario autenticado a partir de su token.
 * Los servicios del cliente usan getClienteId() para consultar SOLO sus propios creditos.
 */
@Component
public class UsuarioActual {

    private Jwt token() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "No hay un usuario autenticado");
        }
        return jwt;
    }

    public String getUsername() {
        return token().getSubject();
    }

    public String getRol() {
        return token().getClaimAsString("rol");
    }

    public boolean esAdmin() {
        return "ADMIN".equals(getRol());
    }

    /** Id del cliente del token. Falla si el usuario no es un cliente. */
    public Long getClienteId() {
        Object valor = token().getClaim("clienteId");
        if (valor == null) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "El usuario no esta asociado a un cliente");
        }
        return ((Number) valor).longValue();
    }
}
