package com.vtesdecks.configuration;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static com.vtesdecks.util.Constants.CARDS_DELETED_HEADER;
import static com.vtesdecks.util.Constants.CONTENT_DISPOSITION_HEADER;
import static com.vtesdecks.util.Constants.DATE_HEADER;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public Jackson2ObjectMapperBuilder jacksonBuilder() {
        final Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder.indentOutput(true);
        builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return builder;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsRegistration cors = registry.addMapping("/**");
        cors.allowedOrigins("*");
        cors.allowedMethods("*");
        cors.exposedHeaders(CARDS_DELETED_HEADER, CONTENT_DISPOSITION_HEADER, DATE_HEADER);
    }
}
