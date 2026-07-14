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
 * access token과 refresh token을 동일한 서명 키로 발급/검증한다. 두 토큰은 만료 시간(TTL)만 다르고
 * 페이로드 구조가 같기 때문에, "이 토큰이 access용인지 refresh용인지"는 이 클래스 밖(어느 저장소/헤더로
 * 왔는지)에서 구분해야 한다 — 토큰 자체에 타입 클레임이 없다는 점에 유의할 것.
 */
@Component
public class JwtTokenProvider {

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
        return createToken(userId, jwtProperties.accessTokenExpiration());
    }

    public String createRefreshToken(UUID userId) {
        return createToken(userId, jwtProperties.refreshTokenExpiration());
    }

    private String createToken(UUID userId, long expirationMillis) {
        Date now = new Date();
        // subject는 userId뿐이라 이메일/권한 등 민감 정보는 페이로드에 담기지 않는다. JWT는
        // 서명만 되고 암호화는 되지 않으므로(base64로 디코딩하면 누구나 읽을 수 있음) 이 점이 중요하다.
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * 서명 검증 + 만료 검사만 수행한다. JWT는 무상태(stateless)이므로 로그아웃/탈취 등으로
     * "폐기"된 토큰이어도 만료 전이면 여기서는 걸러지지 않는다 — access token의 즉시 무효화가
     * 필요하면 별도의 블랙리스트가 있어야 한다. refresh token은 이 검증과 별개로
     * RefreshTokenRepository에 저장된 최신 값과 일치하는지까지 추가로 대조해서 무효화를 흉내낸다.
     */
    public void validateToken(String token) {
        try {
            parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
