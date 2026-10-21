package com.portalcreditos.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Responde 401 en formato JSON cuando falta el token o es invalido/expirado. */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json(401, "Unauthorized",
                "Debe iniciar sesion. El token no se envio, es invalido o ya expiro.", request.getRequestURI()));
    }

    static String json(int estado, String error, String mensaje, String ruta) {
        return "{\"fecha\":\"" + LocalDateTime.now() + "\",\"estado\":" + estado
                + ",\"error\":\"" + error + "\",\"mensaje\":\"" + mensaje
                + "\",\"ruta\":\"" + ruta.replace("\"", "") + "\",\"detalles\":null}";
    }
}
