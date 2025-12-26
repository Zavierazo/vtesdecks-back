package com.vtesdecks.integration;

import com.vtesdecks.model.tcgmarket.MarketResponse;
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

@FeignClient(name = "TcgMarketClient", url = "https://tcg-admin-277076492109.europe-west1.run.app", configuration = TcgMarketClient.Configuration.class)
public interface TcgMarketClient {

    @GetMapping(value = "/api/market/details?order=price&game=Vampire%20The%20Eternal%20Struggle&ptype=SINGLES", produces = APPLICATION_JSON_VALUE)
    MarketResponse getProducts(@RequestParam(name = "page") Integer page, @RequestParam(name = "page_size") Integer pageSize);

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
