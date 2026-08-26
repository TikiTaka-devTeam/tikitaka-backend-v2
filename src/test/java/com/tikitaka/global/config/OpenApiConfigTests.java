package com.tikitaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

class OpenApiConfigTests {
    @Test
    void configuresApiInformationAndJwtBearerScheme() {
        OpenAPI openApi = new OpenApiConfig().tikitakaOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("TikiTaka API");
        SecurityScheme bearerScheme = openApi.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH);
        assertThat(bearerScheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearerScheme.getScheme()).isEqualTo("bearer");
        assertThat(bearerScheme.getBearerFormat()).isEqualTo("JWT");
    }
}
