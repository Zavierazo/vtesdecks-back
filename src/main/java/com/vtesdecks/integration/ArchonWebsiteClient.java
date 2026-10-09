package com.vtesdecks.integration;

import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "ArchonWebsiteClient", url = "${archon.websiteUrl:https://archon.vekn.net}",
        configuration = ArchonClient.Configuration.class)
public interface ArchonWebsiteClient {
    @PostMapping(value = "/auth/login", consumes = APPLICATION_JSON_VALUE)
    WebsiteSession login(@RequestBody LoginRequest request);

    @GetMapping("/snapshot")
    Response snapshot(@RequestParam(value = "token", required = false) String token);

    // Avoid generated toString methods: these objects contain credentials.
    record LoginRequest(String email, String password) {
        @Override
        public String toString() {
            return "ArchonLoginRequest[redacted]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebsiteSession(@JsonProperty("access_token") String accessToken) {
        @Override
        public String toString() {
            return "ArchonWebsiteSession[redacted]";
        }
    }
}
