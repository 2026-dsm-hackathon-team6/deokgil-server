package org.example.deokgilserver.domain.auth.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.jwt.JwtTokenProvider;
import org.example.deokgilserver.common.jwt.TokenType;
import org.example.deokgilserver.domain.auth.presentation.GoogleOAuthClient;
import org.example.deokgilserver.domain.auth.presentation.dto.GoogleUserInfo;
import org.example.deokgilserver.domain.auth.presentation.dto.request.GoogleAuthRequest;
import org.example.deokgilserver.domain.auth.repository.RefreshTokenRepository;
import org.example.deokgilserver.domain.auth.service.dto.RefreshResult;
import org.example.deokgilserver.domain.auth.service.dto.TokenResult;
import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserRole;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final GoogleOAuthClient googleOAuthClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthServiceImpl(
            UserRepository userRepository,
            GoogleOAuthClient googleOAuthClient,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.userRepository = userRepository;
        this.googleOAuthClient = googleOAuthClient;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public TokenResult signUpWithGoogle(GoogleAuthRequest request) {
        if (request.nickname() == null || request.nickname().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        GoogleUserInfo googleUserInfo = googleOAuthClient.getUserInfo(request.authorizationCode());

        if (userRepository.findByGoogleId(googleUserInfo.googleId()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_USER);
        }

        User user = userRepository.save(User.builder()
                .googleId(googleUserInfo.googleId())
                .email(googleUserInfo.email())
                .nickname(request.nickname())
                .profileImage(request.profileImage())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResult loginWithGoogle(GoogleAuthRequest request) {
        GoogleUserInfo googleUserInfo = googleOAuthClient.getUserInfo(request.authorizationCode());

        User user = userRepository.findByGoogleId(googleUserInfo.googleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAW) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        return issueTokens(user);
    }

    /**
     * 검증 순서: (1) JWT 서명/만료 검증 → (2) 회전(rotate)으로 "저장된 최신 토큰과 일치하는지 확인 +
     * 새 토큰으로 교체"를 한 번의 원자적 연산으로 수행. (1)만으로는 부족한 이유는, 서명이 유효한
     * 토큰이라도 이미 rotation으로 교체되어 "폐기된" 토큰일 수 있기 때문이다(JWT는 무상태라 서명
     * 검증만으론 폐기 여부를 알 수 없다). 확인과 교체를 원자적으로 묶은 이유는, 두 단계로 나누면
     * (확인 → 교체) 그 사이에 동시 요청이 끼어들어 같은 refresh token으로 온 요청 두 개가 모두
     * 확인을 통과해버릴 수 있기 때문이다 — RefreshTokenRepository.rotate() 참고.
     */
    @Override
    @Transactional
    public RefreshResult reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        jwtTokenProvider.validateToken(refreshToken, TokenType.REFRESH);
        UUID userId = jwtTokenProvider.getUserId(refreshToken);

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        // 유효하지 않은/이미 회전된 토큰은 여기서 걸러지므로, 폐기된 토큰 하나 때문에 DB 조회까지
        // 가지 않는다(원래 동작 유지).
        if (!refreshTokenRepository.rotate(userId, refreshToken, newRefreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAW) {
            refreshTokenRepository.delete(userId);
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    @Override
    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.delete(userId);
    }

    private TokenResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(user.getId(), refreshToken);

        return new TokenResult(accessToken, refreshToken, UserResponse.from(user));
    }
}
