package com.vtesdecks.integration;

import com.vtesdecks.model.n8n.RAGRequest;
import com.vtesdecks.model.n8n.RAGResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "VtesJudgeAiClient", url = "${n8n.baseUrl:http://localhost:5678}", configuration = N8NClient.Configuration.class)
public interface N8NClient {

    @PostMapping(value = "/webhook/vtes-rag", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    RAGResponse ask(@RequestHeader("Api-Key") String apiKey, @RequestBody RAGRequest request);

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
