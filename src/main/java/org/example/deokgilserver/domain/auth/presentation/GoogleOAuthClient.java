package org.example.deokgilserver.domain.auth.presentation;

import org.example.deokgilserver.domain.auth.presentation.dto.GoogleUserInfo;

public interface GoogleOAuthClient {

    GoogleUserInfo getUserInfo(String authorizationCode);
}
