package com.vtesdecks.integration;

import com.vtesdecks.model.shopify.ProductsResponse;
import feign.Logger;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "GamePodClient", url = "https://www.gamepod.es", configuration = GamePodClient.Configuration.class)
public interface GamePodClient {

    @GetMapping(value = "/products.json", produces = APPLICATION_JSON_VALUE)
    ProductsResponse getProducts(@RequestParam(name = "limit") Integer limit, @RequestParam(name = "page") Integer page);

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
