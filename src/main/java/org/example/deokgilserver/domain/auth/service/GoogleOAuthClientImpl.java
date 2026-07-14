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

/**
 * OAuth 2.0 authorization code flow의 서버 사이드(백엔드) 파트를 담당한다.
 * 인가 코드(authorization code) 자체는 Google 로그인 화면에서 프론트가 받아오고, 이 클래스는
 * 그 코드를 받아 (1) access token으로 교환 → (2) 그 access token으로 사용자 정보를 조회하는,
 * "code를 절대 프론트에서 직접 Google API 호출에 쓰지 않는" 표준 흐름만 수행한다.
 * client_secret은 이 서버(기밀 유지 가능한 환경)에서만 다루고 프론트로 내려가지 않는다는 점이
 * 이 플로우가 안전한 이유다.
 */
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

    /**
     * authorization code는 1회용이며 Google이 발급 시점의 client_id/redirect_uri와 정확히
     * 일치하는 요청에서만 access token으로 교환해준다(둘 중 하나라도 다르면 invalid_grant).
     * 이미 사용됐거나 만료된 code로 재시도해도 여기서 실패하므로, code 탈취 후 재생(replay)
     * 공격의 유효 기간이 매우 짧다.
     */
    // redirect_uri는 프론트가 인가 코드를 받아온 방식과 정확히 일치해야 한다: 프론트가 팝업
    // 플로우(Google Identity Services의 ux_mode: 'popup')를 쓴다면 이 값은 실제 URL이 아니라
    // 문자열 그대로 "postmessage"여야 하고, 리다이렉트 플로우라면 Google Cloud Console에 등록한
    // 실제 콜백 URL이어야 한다. 둘이 안 맞으면 Google이 invalid_grant/redirect_uri_mismatch로
    // 거부한다 — GOOGLE_REDIRECT_URI 환경변수를 프론트의 실제 플로우에 맞게 설정할 것.
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
            // email_verified가 false인 이메일을 신뢰하면, 공격자가 아직 소유권 확인이 안 된
            // (또는 확인 절차가 느슨한) 이메일 주소로 타인의 이메일을 사칭해 가입할 수 있다.
            // 계정 매칭 자체는 email이 아니라 googleId(sub, Google이 보장하는 불변 식별자)로
            // 하므로 계정 탈취까지 이어지진 않지만, 신뢰할 수 없는 이메일이 알림 발송 등에
            // 쓰이는 걸 막기 위해 여기서 걸러낸다.
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
