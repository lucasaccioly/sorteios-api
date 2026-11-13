package com.lucasaccioly.sorteios.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    private static final String DESCRIPTION = """
            API REST de rifas com **sorteio auditável** (commit-reveal), compra de números segura sob \
            concorrência e sorteio automático na data marcada.

            **Como testar em 1 minuto**
            1. `POST /api/auth/login` com `participante@demo.dev` / `demo12345` (ou crie sua conta em `/api/auth/register`).
            2. Copie o `token`, clique em **Authorize** e cole.
            3. `GET /api/raffles?status=OPEN` → escolha uma rifa → `POST /api/raffles/{id}/tickets` com `{"quantity": 2}`.
            4. Veja uma rifa já sorteada (`status=DRAWN`) e a prova em `GET /api/raffles/{id}/proof`.

            Conta de organizadora: `organizador@demo.dev` / `demo12345`.

            ⏳ A demo roda num plano gratuito que hiberna: o primeiro acesso pode levar ~1 min.

            Código: https://github.com/lucasaccioly/sorteios-api""";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Sorteios API").version("1.0").description(DESCRIPTION)
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
