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
     * 검증 순서가 중요하다: (1) JWT 서명/만료 검증 → (2) Redis에 저장된 최신 토큰과 일치하는지 대조.
     * (1)만으로는 부족한 이유는, 서명이 유효한 토큰이라도 이미 rotation으로 교체되어 "폐기된" 토큰일
     * 수 있기 때문이다(JWT는 무상태라 서명 검증만으론 폐기 여부를 알 수 없다). (2)가 바로 그 폐기 여부를
     * Redis 조회로 보완하는 부분 — 탈취된 refresh token이 재사용(replay)되면 여기서 걸린다.
     */
    @Override
    @Transactional
    public RefreshResult reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        jwtTokenProvider.validateToken(refreshToken, TokenType.REFRESH);
        UUID userId = jwtTokenProvider.getUserId(refreshToken);

        if (!refreshTokenRepository.matches(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAW) {
            refreshTokenRepository.delete(userId);
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        // 회전(rotation): 매 재발급마다 access/refresh 토큰을 둘 다 새로 발급하고 Redis 값을 덮어쓴다.
        // 방금 검증에 쓰인 refreshToken(옛 토큰)은 이 시점부터 Redis의 최신 값과 달라지므로 즉시
        // 무효화된다 — 같은 refresh token으로 두 번 재발급을 시도하면 두 번째 요청은 반드시 실패한다.
        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenRepository.save(userId, newRefreshToken);

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
