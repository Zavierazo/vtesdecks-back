package com.vtesdecks.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;

@Slf4j
@Service
public class RecaptchaService {
    private static final float RECAPTCHA_MIN_SCORE = 0.3f;

    private static class RecaptchaResponse {
        @JsonProperty("success")
        private boolean success;
        @JsonProperty("score")
        private Float score;
        @JsonProperty("error-codes")
        private Collection<String> errorCodes;
    }

    @Autowired
    private RestTemplate restTemplate;

    @Value("${recaptcha.url:https://www.google.com/recaptcha/api/siteverify}")
    private String recaptchaUrl;

    @Value("${recaptcha.secret-key}")
    private String recaptchaSecretKey;


    public boolean isResponseValid(String remoteIp, String response) {
        RecaptchaResponse recaptchaResponse;
        try {
            recaptchaResponse =
                    restTemplate.postForEntity(recaptchaUrl, createBody(recaptchaSecretKey, remoteIp, response), RecaptchaResponse.class).getBody();
            if (recaptchaResponse != null && (recaptchaResponse.score == null || recaptchaResponse.score >= RECAPTCHA_MIN_SCORE)) {
                return recaptchaResponse.success;
            }
        } catch (RestClientException e) {
            log.error("Unable to validate recaptcha for {} and response {}", remoteIp, response, e);
        }
        return false;
    }

    private MultiValueMap<String, String> createBody(String secret, String remoteIp, String response) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("remoteip", remoteIp);
        form.add("response", response);
        return form;
    }

}
