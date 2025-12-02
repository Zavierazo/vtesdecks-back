package com.vtesdecks.integration;

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

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@FeignClient(name = "CurrencyExchangeClient", url = "http://currencies.apps.grandtrunk.net", configuration = CurrencyExchangeClient.Configuration.class)
public interface CurrencyExchangeClient {

    @GetMapping(value = "/getlatest/{from}/{to}", produces = TEXT_PLAIN_VALUE)
    String getLatest(@PathVariable String from, @PathVariable String to);


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
