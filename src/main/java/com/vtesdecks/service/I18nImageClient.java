package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class I18nImageClient {
    private static final String CDN_BASE_URL = "https://cdn.vtesdecks.com";
    private final RestTemplate restTemplate;

    public enum Availability { PRESENT, MISSING, FAILED }

    public record Check(CardI18nImageId id, Availability availability) {
    }

    public I18nImageClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        restTemplate = new RestTemplate(factory);
    }

    I18nImageClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public static String imagePath(CardI18nImageId id) {
        return "/img/cards/" + id.getLocale() + "/" + id.getCardId() + ".jpg";
    }

    public Check check(CardI18nImageId id) {
        String url = CDN_BASE_URL + imagePath(id);
        try {
            var response = restTemplate.exchange(url, HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return new Check(id, Availability.PRESENT);
            }
            log.warn("Unexpected status {} checking i18n image {}", response.getStatusCode(), url);
        } catch (HttpClientErrorException.NotFound e) {
            return new Check(id, Availability.MISSING);
        } catch (RestClientException e) {
            log.warn("Unable to check i18n image {}: {}", url, e.getMessage());
        }
        return new Check(id, Availability.FAILED);
    }
}
