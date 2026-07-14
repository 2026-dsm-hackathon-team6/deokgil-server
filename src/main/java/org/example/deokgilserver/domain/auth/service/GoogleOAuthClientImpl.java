package org.example.deokgilserver.domain.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.domain.auth.presentation.GoogleOAuthClient;
import org.example.deokgilserver.domain.auth.presentation.dto.GoogleUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
public class GoogleOAuthClientImpl implements GoogleOAuthClient {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthClientImpl(
            WebClient.Builder webClientBuilder,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret,
            @Value("${spring.security.oauth2.client.registration.google.redirect-uri}") String redirectUri
    ) {
        this.webClient = webClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public GoogleUserInfo getUserInfo(String authorizationCode) {
        String accessToken = requestAccessToken(authorizationCode);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", authorizationCode);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        try {
            GoogleTokenResponse response = webClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(GoogleTokenResponse.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
            }
            return response.accessToken();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google 토큰 발급 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private GoogleUserInfo requestUserInfo(String accessToken) {
        try {
            GoogleUserInfoResponse response = webClient.get()
                    .uri(USERINFO_URI)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(GoogleUserInfoResponse.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (response == null || response.sub() == null || response.email() == null) {
                throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
            }
            if (!Boolean.TRUE.equals(response.emailVerified())) {
                log.warn("Google 계정의 이메일이 인증되지 않았습니다: sub={}", response.sub());
                throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
            }
            return new GoogleUserInfo(response.sub(), response.email());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google 사용자 정보 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("id_token") String idToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleUserInfoResponse(
            String sub,
            String email,
            @JsonProperty("email_verified") Boolean emailVerified
    ) {
    }
}
