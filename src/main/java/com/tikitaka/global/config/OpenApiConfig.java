package com.tikitaka.global.config;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";
    private static final String USER_AUTH_TAG = "사용자/인증 API";
    private static final Pattern USER_API_ID = Pattern.compile("^USR-(\\d{3})\\b");

    @Bean
    OpenAPI tikitakaOpenApi() {
        return new OpenAPI()
                // Resolve API requests against the public origin serving Swagger, even behind a proxy.
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("TikiTaka API")
                        .description("TikiTaka backend REST API")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer userApiOperationOrderCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            List<Map.Entry<String, PathItem>> userApiPaths = openApi.getPaths().entrySet().stream()
                    .filter(entry -> userApiNumber(entry.getValue()).isPresent())
                    .sorted(Comparator.comparingInt(entry -> userApiNumber(entry.getValue()).orElseThrow()))
                    .toList();
            Iterator<Map.Entry<String, PathItem>> sortedUserApis = userApiPaths.iterator();
            Paths sortedPaths = new Paths();

            openApi.getPaths().forEach((path, pathItem) -> {
                if (userApiNumber(pathItem).isPresent()) {
                    Map.Entry<String, PathItem> sortedEntry = sortedUserApis.next();
                    sortedPaths.addPathItem(sortedEntry.getKey(), sortedEntry.getValue());
                } else {
                    sortedPaths.addPathItem(path, pathItem);
                }
            });
            openApi.setPaths(sortedPaths);
        };
    }

    private OptionalInt userApiNumber(PathItem pathItem) {
        return pathItem.readOperations().stream()
                .filter(this::isUserAuthApi)
                .map(Operation::getSummary)
                .filter(summary -> summary != null)
                .map(USER_API_ID::matcher)
                .filter(Matcher::find)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .min();
    }

    private boolean isUserAuthApi(Operation operation) {
        return operation.getTags() != null && operation.getTags().contains(USER_AUTH_TAG);
    }

    @Bean
    OpenApiCustomizer signupMultipartEncodingCustomizer() {
        return openApi -> {
            setSignupDataJsonEncoding(openApi, "/api/v1/auth/signup");
            setSignupDataJsonEncoding(openApi, "/api/v1/auth/oauth/signup");
        };
    }

    private void setSignupDataJsonEncoding(OpenAPI openApi, String path) {
        if (openApi.getPaths() == null || openApi.getPaths().get(path) == null
                || openApi.getPaths().get(path).getPost() == null
                || openApi.getPaths().get(path).getPost().getRequestBody() == null) {
            return;
        }
        io.swagger.v3.oas.models.media.MediaType multipart = openApi.getPaths().get(path).getPost()
                .getRequestBody().getContent().get("multipart/form-data");
        if (multipart != null) {
            multipart.addEncoding("signup_data", new Encoding().contentType("application/json"));
        }
    }

}
