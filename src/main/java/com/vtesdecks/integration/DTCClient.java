package com.vtesdecks.integration;

import com.vtesdecks.model.dtc.Product;
import feign.Logger;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "DTCClient", url = "https://api.drivethrucards.com", configuration = DTCClient.Configuration.class)
public interface DTCClient {

    @GetMapping(value = "/api/vBeta/products", produces = APPLICATION_JSON_VALUE)
    List<Product> getProducts(
            @RequestParam(name = "groupId") Integer groupId,
            @RequestParam(name = "siteId") Integer siteId,
            @RequestParam(name = "status") Integer status,
            @RequestParam(name = "partial") Boolean partial,
            @RequestParam(name = "categories.categoryId[require]") Integer categoryId,
            @RequestParam(name = "page") Integer page,
            @RequestParam(name = "order[newest]") String orderType
    );

    @GetMapping(value = "/api/vBeta/products/{productId}", produces = APPLICATION_JSON_VALUE)
    Product getProduct(
            @PathVariable("productId") Integer productId,
            @RequestParam(name = "groupId") Integer groupId,
            @RequestParam(name = "siteId") Integer siteId
    );

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
