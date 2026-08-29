package com.tikitaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
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

    @Test
    void sortsUserAuthApiOperationsByUsrIdWithinTheGroup() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/usr-014", userApiPath("USR-014 서비스 문의 작성"))
                .addPathItem("/other", new PathItem().get(new Operation().addTagsItem("강의 API").summary("강의 조회")))
                .addPathItem("/usr-005", userApiPath("USR-005 이메일 중복 확인"))
                .addPathItem("/usr-011", userApiPath("USR-011 비밀번호 변경")));

        config.userApiOperationOrderCustomizer().customise(openApi);

        assertThat(openApi.getPaths().keySet())
                .containsExactly("/usr-005", "/other", "/usr-011", "/usr-014");
    }

    private PathItem userApiPath(String summary) {
        return new PathItem().get(new Operation()
                .addTagsItem("사용자/인증 API")
                .summary(summary));
    }
}
