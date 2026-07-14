package org.example.deokgilserver.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cookie")
public record CookieProperties(boolean secure, String sameSite, String domain) {
}
