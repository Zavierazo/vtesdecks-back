package com.vtesdecks.integration;

import com.vtesdecks.model.scanner.ScanRequest;
import com.vtesdecks.model.scanner.ScanResponse;
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

@FeignClient(name = "ScannerClient", url = "${scanner.baseUrl}", configuration = ScannerClient.Configuration.class)
public interface ScannerClient {

    @PostMapping(value = "/scan", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    ScanResponse scan(@RequestBody ScanRequest request);

    class Configuration {
        @Bean
        public Logger.Level feignLoggerLevel() {
            return Logger.Level.FULL;
        }

        @Bean
        public Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> converters) {
            return new SpringFormEncoder(new SpringEncoder(converters));
        }
    }
}

