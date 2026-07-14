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
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private GoogleOAuthClient googleOAuthClient;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private GoogleUserInfo googleUserInfo;

    @BeforeEach
    void setUp() {
        googleUserInfo = new GoogleUserInfo("google-id-123", "test@example.com");
    }

    private User activeUser() {
        return User.builder()
                .googleId("google-id-123")
                .email("test@example.com")
                .nickname("tester")
                .profileImage(null)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void 회원가입에_성공하면_토큰이_발급되고_리프레시_토큰이_저장된다() {
        GoogleAuthRequest request = new GoogleAuthRequest("auth-code", "tester", null);
        when(googleOAuthClient.getUserInfo("auth-code")).thenReturn(googleUserInfo);
        when(userRepository.findByGoogleId("google-id-123")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token");

        TokenResult response = authService.signUpWithGoogle(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().nickname()).isEqualTo("tester");
        verify(refreshTokenRepository).save(any(), eq("refresh-token"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 이미_가입된_구글_계정으로_회원가입하면_DUPLICATE_USER_예외가_발생한다() {
        GoogleAuthRequest request = new GoogleAuthRequest("auth-code", "tester", null);
        when(googleOAuthClient.getUserInfo("auth-code")).thenReturn(googleUserInfo);
        when(userRepository.findByGoogleId("google-id-123")).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> authService.signUpWithGoogle(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_USER);

        verify(userRepository, never()).save(any());
    }

    @Test
    void 닉네임이_비어있으면_회원가입시_INVALID_INPUT_예외가_발생하고_구글_인증을_시도하지_않는다() {
        GoogleAuthRequest request = new GoogleAuthRequest("auth-code", " ", null);

        assertThatThrownBy(() -> authService.signUpWithGoogle(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(googleOAuthClient);
        verify(userRepository, never()).save(any());
    }

    @Test
    void 닉네임이_null이면_회원가입시_INVALID_INPUT_예외가_발생한다() {
        GoogleAuthRequest request = new GoogleAuthRequest("auth-code", null, null);

        assertThatThrownBy(() -> authService.signUpWithGoogle(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 로그인에_성공하면_토큰이_발급된다() {
        GoogleAuthRequest request = new GoogleAuthRequest("auth-code", null, null);
        when(googleOAuthClient.getUserInfo("auth-code")).thenReturn(googleUserInfo);
        when(userRepository.findByGoogleId("google-id-123")).thenReturn(Optional.of(activeUser()));
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token");

        TokenResult response = authService.loginWithGoogle(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(refreshTokenRepository).save(any(), eq("refresh-token"));
    }

    @Test
    void 가입되지_않은_사용자가_로그인하면_USER_NOT_FOUND_예외가_발생한다() {
        GoogleAuthRequest request = new GoogleAuthRequest("auth-code", null, null);
        when(googleOAuthClient.getUserInfo("auth-code")).thenReturn(googleUserInfo);
        when(userRepository.findByGoogleId("google-id-123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithGoogle(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 탈퇴한_사용자가_로그인하면_WITHDRAWN_USER_예외가_발생하고_토큰이_발급되지_않는다() {
        GoogleAuthRequest request = new GoogleAuthRequest("auth-code", null, null);
        User withdrawnUser = User.builder()
                .googleId("google-id-123")
                .email("test@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .status(UserStatus.WITHDRAW)
                .build();
        when(googleOAuthClient.getUserInfo("auth-code")).thenReturn(googleUserInfo);
        when(userRepository.findByGoogleId("google-id-123")).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> authService.loginWithGoogle(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_USER);

        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void 유효한_리프레시_토큰이면_토큰이_재발급되고_기존_토큰은_회전된다() {
        UUID userId = UUID.randomUUID();
        User user = activeUser();
        when(jwtTokenProvider.getUserId("old-refresh-token")).thenReturn(userId);
        when(refreshTokenRepository.matches(userId, "old-refresh-token")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(userId)).thenReturn("new-refresh-token");

        RefreshResult result = authService.reissue("old-refresh-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        verify(jwtTokenProvider).validateToken("old-refresh-token", org.example.deokgilserver.common.jwt.TokenType.REFRESH);
        verify(refreshTokenRepository).save(userId, "new-refresh-token");
    }

    @Test
    void 저장된_값과_일치하지_않는_리프레시_토큰은_INVALID_TOKEN_예외가_발생한다() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.getUserId("reused-token")).thenReturn(userId);
        when(refreshTokenRepository.matches(userId, "reused-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.reissue("reused-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void 쿠키가_없어_빈_리프레시_토큰이_오면_INVALID_TOKEN_예외가_발생한다() {
        assertThatThrownBy(() -> authService.reissue(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 탈퇴한_사용자의_리프레시_토큰은_WITHDRAWN_USER_예외가_발생하고_토큰이_삭제된다() {
        UUID userId = UUID.randomUUID();
        User withdrawnUser = User.builder()
                .googleId("google-id-123")
                .email("test@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .status(UserStatus.WITHDRAW)
                .build();
        when(jwtTokenProvider.getUserId("old-refresh-token")).thenReturn(userId);
        when(refreshTokenRepository.matches(userId, "old-refresh-token")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> authService.reissue("old-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_USER);

        verify(refreshTokenRepository).delete(userId);
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    void 로그아웃하면_리프레시_토큰이_삭제된다() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId);

        verify(refreshTokenRepository).delete(userId);
    }
}
