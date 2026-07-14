package org.example.deokgilserver.domain.auth.service;

import org.example.deokgilserver.domain.auth.presentation.dto.request.GoogleAuthRequest;
import org.example.deokgilserver.domain.auth.service.dto.RefreshResult;
import org.example.deokgilserver.domain.auth.service.dto.TokenResult;

import java.util.UUID;

public interface AuthService {

    TokenResult signUpWithGoogle(GoogleAuthRequest request);

    TokenResult loginWithGoogle(GoogleAuthRequest request);

    RefreshResult reissue(String refreshToken);

    void logout(UUID userId);
}
