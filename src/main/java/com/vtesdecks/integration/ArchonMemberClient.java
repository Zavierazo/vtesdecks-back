package com.vtesdecks.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;

/** Member OAuth is separate from the retrying Public API/export client. */
@Component
public class ArchonMemberClient {
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String memberUrl;
    private final RestClient http;

    public ArchonMemberClient(@Value("${archon.clientId:}") String clientId,
                              @Value("${archon.clientSecret:}") String clientSecret,
                              @Value("${archon.redirectUri:}") String redirectUri,
                              @Value("${archon.memberUrl:https://archon.vekn.net}") String memberUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.memberUrl = memberUrl;
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(memberUrl).requestFactory(factory).build();
    }

    public boolean available() {
        return StringUtils.isNoneBlank(clientId, clientSecret, redirectUri);
    }

    public String authorizationUrl(String state, String challenge) {
        return UriComponentsBuilder.fromUriString(memberUrl + "/consent")
                .queryParam("response_type", "code").queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri).queryParam("scope", "profile:read")
                .queryParam("state", state).queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").build().encode().toUriString();
    }

    public Tokens exchange(String code, String verifier) {
        var form = credentials();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", verifier);
        // No retry: the authorization code is single-use and only lives for 60 seconds.
        return http.post().uri("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Tokens.class);
    }

    public Member userInfo(String token) {
        return http.get().uri("/oauth/userinfo").headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(Member.class);
    }

    public void revoke(String token) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        var form = credentials();
        form.add("token", token);
        http.post().uri("/oauth/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().toBodilessEntity();
    }

    private MultiValueMap<String, String> credentials() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    public record Tokens(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken,
                         @JsonProperty("token_type") String tokenType, String scope) {
        @Override public String toString() { return "Archon tokens [redacted]"; }
    }

    public record Member(String sub, @JsonProperty("vekn_id") String veknId) {
    }
}
