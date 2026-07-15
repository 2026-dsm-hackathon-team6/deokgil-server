package org.example.deokgilserver.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * access token과 refresh token을 동일한 서명 키로 발급/검증하지만, 페이로드에 담기는
 * "type" 클레임으로 용도를 구분한다. 이 구분이 없으면 두 토큰이 서명/만료 검증만으로는
 * 구별되지 않아, 탈취한 refresh token을 Authorization 헤더에 그대로 실어 access token
 * 대신 쓸 수 있다 — validateToken(token, expectedType)이 항상 타입 일치까지 확인해서 막는다.
 */
@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "type";

    private final SecretKey key;
    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // HMAC-SHA 대칭키. jjwt가 alg 헤더를 직접 신뢰하지 않고 signWith(key)에서 키 타입으로
        // 알고리즘을 강제하므로, 토큰의 alg를 조작해 서명 검증을 우회하는 "algorithm confusion"
        // 공격(예: alg=none, HS/RS 키 혼용)이 통하지 않는다.
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId) {
        return createToken(userId, jwtProperties.accessTokenExpiration(), TokenType.ACCESS);
    }

    public String createRefreshToken(UUID userId) {
        return createToken(userId, jwtProperties.refreshTokenExpiration(), TokenType.REFRESH);
    }

    private String createToken(UUID userId, long expirationMillis, TokenType type) {
        Date now = new Date();
        // subject는 userId뿐이라 이메일/권한 등 민감 정보는 페이로드에 담기지 않는다. JWT는
        // 서명만 되고 암호화는 되지 않으므로(base64로 디코딩하면 누구나 읽을 수 있음) 이 점이 중요하다.
        return Jwts.builder()
                .subject(userId.toString())
                .claim(TYPE_CLAIM, type.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * 서명 검증 + 만료 검사 + 토큰 타입 일치 여부까지 확인한다. JWT는 무상태(stateless)이므로
     * 로그아웃/탈취 등으로 "폐기"된 토큰이어도 만료 전이면 여기서는 걸러지지 않는다 — access
     * token의 즉시 무효화가 필요하면 별도의 블랙리스트가 있어야 한다. refresh token은 이 검증과
     * 별개로 RefreshTokenRepository에 저장된 최신 값과 일치하는지까지 추가로 대조해서 무효화를 흉내낸다.
     *
     * expectedType 검증이 핵심이다 — 이게 없으면 access/refresh 토큰이 서명·만료 구조상
     * 구별되지 않아 refresh token으로 일반 API를 호출할 수 있게 된다.
     */
    public void validateToken(String token, TokenType expectedType) {
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 이 필드가 없는 토큰(타입 클레임 도입 이전에 발급된 토큰)은 안전한 기본값이 없으므로
        // 무조건 거부한다 — 재로그인을 한 번 강제하는 게, 용도를 알 수 없는 토큰을 신뢰하는
        // 것보다 안전하다.
        String actualType = claims.get(TYPE_CLAIM, String.class);
        if (!expectedType.name().equals(actualType)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * 로그아웃 전용: 서명은 반드시 유효해야 하지만 만료는 허용한다. Authorization 헤더는
     * 쿠키가 아니라 SameSite 제약을 받지 않으므로, 쿠키(refresh token)가 cross-site 요청이라
     * 브라우저에 의해 아예 전송되지 않는 상황에서도 access token으로 로그아웃 대상 사용자를
     * 식별할 수 있게 해준다. 만료된 access token은 일반 API 인증에는 못 쓰지만(validateToken이
     * 거부), "이 refresh token을 지워라"는 자기 자신에 대한 로그아웃 요청에 한해서는 서명만
     * 유효하면 굳이 만료 여부를 따질 이유가 없다 — 어차피 지우는 대상은 그 토큰이 가리키는
     * 사용자 자신의 세션뿐이라 위조된 서명이 아닌 한 악용 여지가 없다.
     */
    public UUID getUserIdIgnoringExpiration(String token, TokenType expectedType) {
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (ExpiredJwtException e) {
            claims = e.getClaims();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String actualType = claims.get(TYPE_CLAIM, String.class);
        if (!expectedType.name().equals(actualType)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return UUID.fromString(claims.getSubject());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
