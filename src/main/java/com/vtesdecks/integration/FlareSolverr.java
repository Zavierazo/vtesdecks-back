package com.vtesdecks.integration;

import com.vtesdecks.model.flaresolverr.FlareRequest;
import com.vtesdecks.model.flaresolverr.FlareResponse;
import feign.Logger;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "FlareSolverr", url = "${flaresolverr.url:http://localhost:8191}", configuration = FlareSolverr.Configuration.class)
public interface FlareSolverr {

    @PostMapping(value = "/v1", produces = APPLICATION_JSON_VALUE)
    FlareResponse getPage(@RequestBody FlareRequest request);

    class Configuration {
        @Bean
        public Logger.Level feignLoggerLevel() {
            return Logger.Level.NONE;
        }

        @Bean
        public Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> converters) {
            return new SpringFormEncoder(new SpringEncoder(converters));
        }
    }
}
