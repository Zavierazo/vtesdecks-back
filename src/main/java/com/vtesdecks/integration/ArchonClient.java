package com.vtesdecks.integration;

import com.vtesdecks.model.archon.TournamentsResponse;
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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "ArchonClient", url = "https://archon.vekn.net", configuration = ArchonClient.Configuration.class)
public interface ArchonClient {

    @GetMapping(value = "/api/tournaments", produces = APPLICATION_JSON_VALUE)
    TournamentsResponse getTournaments(@RequestParam(name = "states") String state, @RequestParam(name = "uid") String uid, @RequestParam(name = "date") String date);

    @GetMapping(value = "/api/tournaments/{uid}/decks", produces = APPLICATION_JSON_VALUE)
    TournamentsResponse getDecks(@PathVariable("uid") Integer productId);

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
