package com.vtesdecks.integration;

import com.vtesdecks.model.market.CardOffersResponse;
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

@FeignClient(name = "MarketBloodLibraryClient", url = "https://market.bloodlibrary.info", configuration = MarketClient.Configuration.class)
public interface MarketClient {

    @GetMapping(value = "/api/card_offers", produces = APPLICATION_JSON_VALUE)
    CardOffersResponse getCardOffers(@RequestParam(name = "offset") Integer offset, @RequestParam(name = "limit") Integer limit);

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
