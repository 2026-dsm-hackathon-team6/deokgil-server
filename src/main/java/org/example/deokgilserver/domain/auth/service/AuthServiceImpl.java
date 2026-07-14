package org.example.deokgilserver.domain.auth.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.jwt.JwtTokenProvider;
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

    @Override
    @Transactional
    public RefreshResult reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        jwtTokenProvider.validateToken(refreshToken);
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

        // 회전(rotation): 재사용된 refresh token은 다음 요청부터 즉시 무효화된다.
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
