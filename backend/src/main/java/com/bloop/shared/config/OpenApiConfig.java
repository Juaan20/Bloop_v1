package com.bloop.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configura Swagger UI (http://localhost:8080/swagger-ui.html) con autenticacion Bearer. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bloopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bloop API")
                        .version("0.1.0")
                        .description("API REST de Bloop. Haz login en POST /api/auth/login y pulsa Authorize con el accessToken."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
