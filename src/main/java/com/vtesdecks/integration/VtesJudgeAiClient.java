package com.vtesdecks.integration;

import com.vtesdecks.model.vtesjudgeai.AskRequest;
import com.vtesdecks.model.vtesjudgeai.AskResponse;
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

@FeignClient(name = "VtesJudgeAiClient", url = "${vtesjudgeai.url:http://vtes-judge-ai:8080}", configuration = VtesJudgeAiClient.Configuration.class)
public interface VtesJudgeAiClient {

    @PostMapping(value = "/ask", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    AskResponse getAmaranthDeck(@RequestBody AskRequest request);

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
