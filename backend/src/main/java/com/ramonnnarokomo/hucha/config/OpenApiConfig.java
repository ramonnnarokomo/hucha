package com.ramonnnarokomo.hucha.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI huchaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hucha API")
                        .version("1.0")
                        .description("Banca digital de demostración: cuentas con IBAN, libro mayor de doble entrada, "
                                + "transferencias con idempotencia y verificación por código, tarjetas, huchas y back-office."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
