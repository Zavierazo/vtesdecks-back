package com.vtesdecks.integration;

import com.vtesdecks.model.currencyexchange.CurrencyResponse;
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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "CurrencyExchangeClient", url = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest", configuration = CurrencyExchangeClient.Configuration.class)
public interface CurrencyExchangeClient {

    @GetMapping(value = "/v1/currencies/{from}.json", produces = APPLICATION_JSON_VALUE)
    CurrencyResponse getLatest(@PathVariable String from);


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
