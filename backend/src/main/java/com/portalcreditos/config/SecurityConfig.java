package com.portalcreditos.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.portalcreditos.security.JsonAccessDeniedHandler;
import com.portalcreditos.security.JsonAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Configuracion de seguridad de la API.
 *
 * - API sin estado (stateless): no hay sesiones en el servidor; cada peticion trae su token JWT.
 * - El token se firma con HMAC-SHA256 usando un secreto que NO esta en el repositorio.
 * - Control de acceso por rol: /api/cliente/** solo CLIENTE, /api/admin/** solo ADMIN.
 * - Rutas publicas: login, /api/publico/** (canales, requisitos, verificacion de paz y salvo) y Swagger.
 * - Contrasenas cifradas con BCrypt.
 */
@Configuration
public class SecurityConfig {

    private final SecretKey claveJwt;
    private final List<String> origenesPermitidos;

    public SecurityConfig(@Value("${app.jwt.secreto}") String secretoBase64,
                          @Value("${app.cors.origenes-permitidos}") String origenes) {
        byte[] bytes = Base64.getDecoder().decode(secretoBase64);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secreto debe tener al menos 256 bits (32 bytes en Base64)");
        }
        this.claveJwt = new SecretKeySpec(bytes, "HmacSHA256");
        this.origenesPermitidos = Arrays.stream(origenes.split(",")).map(String::trim).toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JsonAuthenticationEntryPoint entryPoint,
                                                   JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // CSRF protege sesiones con cookies; esta API usa tokens en cabecera, por eso se desactiva
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/salud", "/api/publico/**", "/error").permitAll()
                        // Documentacion de la API (Swagger UI)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**", "/api/diagnostico/**").hasRole("ADMIN")
                        .requestMatchers("/api/cliente/**").hasRole("CLIENTE")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorRoles()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** Lee el claim "rol" del token y lo convierte en la autoridad ROLE_CLIENTE o ROLE_ADMIN. */
    private JwtAuthenticationConverter convertidorRoles() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Permite que el frontend Angular (otro puerto) consuma la API desde el navegador. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origenesPermitidos);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
