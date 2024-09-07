package com.vtesdecks.configuration;

import static springfox.documentation.spring.web.paths.Paths.ROOT;
import static springfox.documentation.spring.web.paths.Paths.removeAdjacentForwardSlashes;

import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;

import springfox.documentation.PathProvider;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
public class SpringFoxConfig {

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
            .select()
            .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.ant("/api/**"))
            .build()

            .host("api.vtesdecks.com")
            .pathProvider(new CustomPathProvider())
            .apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        return new ApiInfo(
            "Vtes Decks REST API",
            "Api used by vtesdecks website",
            "1.0",
            "#",
            new Contact("Zavierazo", "vtesdecks.com", "support@vtesdecks.com"),
            "",
            "#",
            Collections.emptyList());
    }

    public class CustomPathProvider implements PathProvider {


        @Override
        public String getOperationPath(String originalPath) {
            String operationPath = originalPath.replace("/api/1.0", "/1.0");
            UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath("/");
            return removeAdjacentForwardSlashes(uriComponentsBuilder.path(operationPath).build().toString());
        }

        @Override
        public String getResourceListingPath(String groupName, String apiDeclaration) {
            String candidate = agnosticUriComponentBuilder(ROOT)
                .pathSegment(groupName, apiDeclaration)
                .build()
                .toString();
            return removeAdjacentForwardSlashes(candidate);
        }

        private UriComponentsBuilder agnosticUriComponentBuilder(String url) {
            UriComponentsBuilder uriComponentsBuilder;
            if (url.startsWith("http")) {
                uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);
            } else {
                uriComponentsBuilder = UriComponentsBuilder.fromPath(url);
            }
            return uriComponentsBuilder;
        }
    }
}
