package com.spot.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.spot.common.UnauthorizedException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NaverOAuthClientImpl implements NaverOAuthClient {

    private static final String AUTHORIZE_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String PROFILE_URL = "https://openapi.naver.com/v1/nid/me";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient restClient = RestClient.create();

    public NaverOAuthClientImpl(
        @Value("${spot.oauth.naver.client-id}") String clientId,
        @Value("${spot.oauth.naver.client-secret}") String clientSecret,
        @Value("${spot.oauth.naver.redirect-uri}") String redirectUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    @Override
    public NaverProfile fetchProfile(String code, String state) {
        String accessToken = exchangeCodeForToken(code, state);

        ProfileResponse profile = restClient.get()
            .uri(PROFILE_URL)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(ProfileResponse.class);

        if (profile == null || profile.response() == null || !StringUtils.hasText(profile.response().id())) {
            throw new UnauthorizedException("네이버 프로필 조회에 실패했습니다.");
        }
        ProfileResponse.Response r = profile.response();
        String nickname = StringUtils.hasText(r.nickname()) ? r.nickname() : "사용자";
        return new NaverProfile(r.id(), r.email(), nickname);
    }

    private String exchangeCodeForToken(String code, String state) {
        URI uri = UriComponentsBuilder.fromUriString(TOKEN_URL)
            .queryParam("grant_type", "authorization_code")
            .queryParam("client_id", clientId)
            .queryParam("client_secret", clientSecret)
            .queryParam("code", code)
            .queryParam("state", state)
            .build()
            .toUri();

        TokenResponse token = restClient.get()
            .uri(uri)
            .retrieve()
            .body(TokenResponse.class);

        if (token == null || !StringUtils.hasText(token.accessToken())) {
            throw new UnauthorizedException("네이버 인증에 실패했습니다.");
        }
        return token.accessToken();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProfileResponse(String resultcode, String message, Response response) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Response(String id, String email, String nickname) {
        }
    }
}
