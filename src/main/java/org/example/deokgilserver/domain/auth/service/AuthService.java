package org.example.deokgilserver.domain.auth.service;

import org.example.deokgilserver.domain.auth.presentation.dto.request.GoogleAuthRequest;
import org.example.deokgilserver.domain.auth.service.dto.RefreshResult;
import org.example.deokgilserver.domain.auth.service.dto.TokenResult;

public interface AuthService {

    TokenResult signUpWithGoogle(GoogleAuthRequest request);

    TokenResult loginWithGoogle(GoogleAuthRequest request);

    RefreshResult reissue(String refreshToken);

    /**
     * refreshToken(쿠키)을 우선 신뢰하고, 없거나 유효하지 않으면 accessToken(Authorization
     * 헤더, 만료돼도 무방)으로 대상 사용자를 식별해 폴백한다 - refresh_token 쿠키가 cross-site
     * 요청이라 브라우저에 의해 아예 전송되지 않는 경우에도 서버 측 세션(Redis)이 실제로
     * 무효화되게 하기 위함이다.
     */
    void logout(String refreshToken, String accessToken);
}
