package com.vtesdecks.integration;

import com.vtesdecks.model.archon.ArchonAccessToken;
import feign.Logger;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "ArchonClient", url = "${archon.url:https://api.archon.vekn.net}", configuration = ArchonClient.Configuration.class)
public interface ArchonClient {

    @PostMapping(value = "/oauth/token", consumes = APPLICATION_FORM_URLENCODED_VALUE, produces = APPLICATION_JSON_VALUE)
    ArchonAccessToken token(@RequestBody MultiValueMap<String, String> form);

    @GetMapping("/v1/export")
    Response export(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    class Configuration {
        private static final int HTTP_TOO_MANY_REQUESTS = 429;
        private static final long INITIAL_RETRY_MILLIS = 5_000L;
        private static final long MAX_RETRY_MILLIS = 60_000L;
        private static final int MAX_ATTEMPTS = 4;

        @Bean
        public Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> converters) {
            return new SpringFormEncoder(new SpringEncoder(converters));
        }

        @Bean
        public ErrorDecoder archonErrorDecoder() {
            return new ArchonErrorDecoder();
        }

        @Bean
        public Retryer archonRetryer() {
            return new Retryer.Default(INITIAL_RETRY_MILLIS, MAX_RETRY_MILLIS, MAX_ATTEMPTS);
        }

        @Bean
        public Logger.Level feignLoggerLevel() {
            return Logger.Level.NONE;
        }

        private static class ArchonErrorDecoder implements ErrorDecoder {
            private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

            @Override
            public Exception decode(String methodKey, Response response) {
                if (response.status() == HTTP_TOO_MANY_REQUESTS) {
                    Exception exception = defaultDecoder.decode(methodKey, response);
                    if (exception instanceof RetryableException retryableException) {
                        return retryableException;
                    }
                    return new RetryableException(
                            response.status(),
                            "Archon rate limit reached",
                            response.request().httpMethod(),
                            (Long) null,
                            response.request()
                    );
                }
                return defaultDecoder.decode(methodKey, response);
            }
        }
    }
}
