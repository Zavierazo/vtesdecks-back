package com.vtesdecks.integration;

import com.fasterxml.jackson.databind.JsonNode;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@FeignClient(name = "PatreonClient", url = "${patreon.api.url:https://www.patreon.com}", configuration = PatreonClient.Configuration.class)
public interface PatreonClient {
    @GetMapping("/api/oauth2/v2/campaigns/{campaignId}/members")
    JsonNode memberPage(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                        @PathVariable("campaignId") String campaignId,
                        @SpringQueryMap Map<String, Object> query);

    class Configuration {
        @Bean
        public Logger.Level patreonLoggerLevel() {
            return Logger.Level.NONE;
        }

        @Bean
        public Retryer patreonRetryer() {
            return Retryer.NEVER_RETRY;
        }

        @Bean
        public Request.Options patreonRequestOptions() {
            return new Request.Options(5, TimeUnit.SECONDS, 20, TimeUnit.SECONDS, false);
        }
    }
}
