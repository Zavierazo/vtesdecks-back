package com.vtesdecks.integration;

import com.vtesdecks.model.krcg.Card;
import com.vtesdecks.model.krcg.Deck;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "KRCGClient", url = "https://api.krcg.org", configuration = KRCGClient.Configuration.class)
public interface KRCGClient {

    @PostMapping(value = "/amaranth", consumes = APPLICATION_FORM_URLENCODED, produces = APPLICATION_JSON_VALUE)
    Deck getAmaranthDeck(@RequestBody Map<String, ?> form);

    @PostMapping(value = "/vdb", consumes = APPLICATION_FORM_URLENCODED, produces = APPLICATION_JSON_VALUE)
    Deck getVDBDeck(@RequestBody Map<String, ?> form);


    @GetMapping(value = "/card/{id}", produces = APPLICATION_JSON_VALUE)
    Card getCard(@PathVariable("id") Integer id);

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
