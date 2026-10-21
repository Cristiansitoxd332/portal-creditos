package com.portalcreditos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion interactiva de la API con Swagger UI: http://localhost:8080/swagger-ui.html
 * Incluye el boton "Authorize" para pegar el token JWT y probar los endpoints protegidos.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI documentacionApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API - Portal de Consulta de Creditos Vehiculares")
                        .version("1.0.0")
                        .description("Prototipo academico con DATOS FICTICIOS. No esta conectado a ningun sistema "
                                + "de la empresa ni procesa pagos reales.\n\n"
                                + "Para probar: POST /api/auth/login, copie el token y uselo en el boton Authorize."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
