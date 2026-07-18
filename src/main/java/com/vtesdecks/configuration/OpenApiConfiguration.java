package com.vtesdecks.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {
    private static final String JWT_SCHEME = "JwtToken";
    private static final String API_PREFIX = "/api";
    private static final String AUTHENTICATED_PREFIX = "/1.0/user";

    @Bean
    public OpenAPI openAPI(@Value("${vtesdecks.openapi.server-url}") String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("VTES Decks API")
                        .version("1.0")
                        .description("REST API for vtesdecks.com"))
                // Explicit server suppresses springdoc's request-derived "Generated server url",
                // which would be wrong both locally and behind the live proxy.
                .servers(List.of(new Server().url(serverUrl)))
                .components(new Components().addSecuritySchemes(JWT_SCHEME,
                        new SecurityScheme()
                                // APIKEY instead of HTTP/bearer: JWTAuthorizationFilter reads the
                                // raw Authorization header value, without a "Bearer " prefix.
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("Raw JWT token sent as-is in the Authorization header (no 'Bearer ' prefix)")));
    }

    /**
     * Rewrites the generated spec to the public path shape: the live reverse proxy prepends
     * /api to every request, so the documented paths must not include it.
     */
    @Bean
    public OpenApiCustomizer apiPrefixCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            Paths publicPaths = new Paths();
            publicPaths.setExtensions(openApi.getPaths().getExtensions());
            openApi.getPaths().forEach((path, item) -> {
                String publicPath = path.startsWith(API_PREFIX + "/") ? path.substring(API_PREFIX.length()) : path;
                if (publicPath.startsWith(AUTHENTICATED_PREFIX)) {
                    item.readOperations().forEach(operation ->
                            operation.addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME)));
                }
                publicPaths.addPathItem(publicPath, item);
            });
            openApi.setPaths(publicPaths);
        };
    }
}
