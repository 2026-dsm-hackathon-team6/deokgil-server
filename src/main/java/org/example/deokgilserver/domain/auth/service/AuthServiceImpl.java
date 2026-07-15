package org.example.deokgilserver.domain.auth.service;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

        log.info("회원가입 완료: userId={}", user.getId());
        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResult loginWithGoogle(GoogleAuthRequest request) {
        GoogleUserInfo googleUserInfo = googleOAuthClient.getUserInfo(request.authorizationCode());

        User user = userRepository.findByGoogleId(googleUserInfo.googleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 탈퇴가 물리 삭제(UserServiceImpl.withdraw())로 바뀌면서 신규로는 이 상태에 도달하지
        // 않지만, 과거 소프트 삭제 방식으로 저장된 레거시 유저 데이터가 남아있을 수 있어 방어 차원으로 유지한다.
        if (user.getStatus() == UserStatus.WITHDRAW) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        log.info("로그인 완료: userId={}", user.getId());
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

        // 위와 동일: 신규 탈퇴는 물리 삭제라 이 분기에 새로 도달하지 않지만, 레거시 소프트 삭제
        // 데이터 방어 차원으로 유지한다.
        if (user.getStatus() == UserStatus.WITHDRAW) {
            refreshTokenRepository.delete(userId);
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        log.info("토큰 재발급 완료: userId={}", userId);
        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    /**
     * refresh token(쿠키)을 우선 신뢰한다 - 없거나 유효하지 않으면 access token(Authorization
     * 헤더)으로 폴백한다. 헤더는 쿠키와 달리 SameSite 제약을 받지 않아 cross-site 요청에서도
     * 항상 전송되므로, refresh_token 쿠키가 브라우저 정책상 아예 안 실리는 상황(프론트/백엔드가
     * 서로 다른 사이트인 배포)에서도 Redis의 세션을 실제로 무효화할 수 있다. access token은
     * 만료됐어도 서명만 유효하면 받아들인다(JwtTokenProvider.getUserIdIgnoringExpiration 참고) -
     * 로그아웃은 access token의 정상 인증 용도가 아니라 "이 사용자의 세션을 지워라"는 자기
     * 자신에 대한 요청이라 만료 여부가 중요하지 않기 때문이다.
     *
     * 두 토큰 다 없거나 둘 다 유효하지 않아도 예외를 던지지 않는다 - 클라이언트 입장에서
     * 로그아웃은 항상 성공해야 하고(이미 로그아웃된 상태를 에러로 취급할 이유가 없음), 이
     * 경우는 애초에 지울 세션이 없다는 뜻일 뿐이다.
     */
    @Override
    @Transactional
    public void logout(String refreshToken, String accessToken) {
        UUID userId = resolveUserIdForLogout(refreshToken, accessToken);
        if (userId != null) {
            refreshTokenRepository.delete(userId);
            log.info("로그아웃 완료: userId={}", userId);
        }
    }

    private UUID resolveUserIdForLogout(String refreshToken, String accessToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                jwtTokenProvider.validateToken(refreshToken, TokenType.REFRESH);
                return jwtTokenProvider.getUserId(refreshToken);
            } catch (BusinessException ignored) {
                // refresh token이 없거나 무효하면 access token 폴백을 시도한다.
            }
        }

        if (accessToken != null && !accessToken.isBlank()) {
            try {
                return jwtTokenProvider.getUserIdIgnoringExpiration(accessToken, TokenType.ACCESS);
            } catch (BusinessException ignored) {
                return null;
            }
        }

        return null;
    }

    private TokenResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(user.getId(), refreshToken);

        return new TokenResult(accessToken, refreshToken, UserResponse.from(user));
    }
}
